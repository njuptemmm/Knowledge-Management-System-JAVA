package com.njupt.kms.qa.index;

import com.njupt.kms.qa.chunk.ChunkingService;
import com.njupt.kms.qa.config.RagProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class IndexingServiceTest {

    /** 产生确定性假向量，并记录被向量化的文本，用于断言「正文只被向量化一次」。 */
    static class CountingEmbeddingModel implements EmbeddingModel {
        final AtomicInteger calls = new AtomicInteger();
        final List<String> embeddedTexts = java.util.Collections.synchronizedList(new ArrayList<>());

        @Override
        public EmbeddingResponse call(EmbeddingRequest request) {
            List<String> inputs = request.getInstructions();
            List<Embedding> results = new ArrayList<>(inputs.size());
            for (int i = 0; i < inputs.size(); i++) {
                results.add(new Embedding(vec(inputs.get(i)), i));
            }
            return new EmbeddingResponse(results);
        }

        @Override
        public float[] embed(Document document) {
            calls.incrementAndGet();
            embeddedTexts.add(document.getText());
            return vec(document.getText());
        }

        @Override
        public float[] embed(String text) {
            calls.incrementAndGet();
            embeddedTexts.add(text);
            return vec(text);
        }

        long timesEmbedded(String text) {
            return embeddedTexts.stream().filter(text::equals).count();
        }

        private float[] vec(String text) {
            float[] vector = new float[8];
            int seed = text == null ? 0 : Math.abs(text.hashCode());
            for (int i = 0; i < vector.length; i++) {
                vector[i] = (seed % 89) + i * 0.5f;
            }
            return vector;
        }

        @Override
        public int dimensions() {
            return 8;
        }
    }

    private CountingEmbeddingModel embedding;
    private SimpleVectorStore vectorStore;
    private RagProperties properties;
    private IndexingService service;

    @BeforeEach
    void setUp() {
        embedding = new CountingEmbeddingModel();
        vectorStore = SimpleVectorStore.builder(embedding).build();

        properties = new RagProperties();
        properties.setEnabled(true);
        // 指向 target 下的临时位置，既不污染工作目录也不进 git
        properties.getVectorStore().setPath("target/test-rag/vector-index.json");

        Executor direct = Runnable::run;   // 同步执行，便于断言
        service = new IndexingService(vectorStore, new ChunkingService(60, 15), direct, properties,
                itemId -> new ItemContentLoader.ItemContent("条目" + itemId, "条目 " + itemId + " 的正文内容。"));
    }

    private List<Document> search(String query) {
        return vectorStore.similaritySearch(SearchRequest.builder()
                .query(query).topK(50).similarityThreshold(0.0).build());
    }

    @Test
    void index_item_writes_chunks_with_item_metadata() {
        service.indexItem(9L, "标题", "一段足够短的正文。");

        List<Document> documents = search("正文");
        assertThat(documents).isNotEmpty();
        assertThat(documents).allSatisfy(d -> assertThat(d.getMetadata())
                .containsEntry("itemId", "9")
                .containsKey("title")
                .containsKey("headingPath"));
    }

    @Test
    void reindexing_same_item_replaces_old_chunks_instead_of_appending() {
        service.indexItem(9L, "标题", "第一版正文内容。");
        int afterFirst = search("正文").size();

        service.indexItem(9L, "标题", "第二版完全不同的正文。");

        // 未先删旧块的话，第二次索引会让块数翻倍
        assertThat(search("正文")).hasSize(afterFirst);
    }

    @Test
    void remove_item_deletes_only_that_items_chunks() {
        service.indexItem(1L, "A", "甲的正文。");
        service.indexItem(2L, "B", "乙的正文。");

        service.removeItem(1L);

        List<Document> remaining = search("正文");
        assertThat(remaining).isNotEmpty();
        assertThat(remaining).allSatisfy(d ->
                assertThat(d.getMetadata()).containsEntry("itemId", "2"));
    }

    @Test
    void removing_an_absent_item_is_a_no_op() {
        service.indexItem(1L, "A", "甲的正文。");

        service.removeItem(999L);

        assertThat(search("正文")).hasSize(1);
    }

    @Test
    void blank_content_indexes_nothing_and_does_not_fail() {
        service.indexItem(9L, "空条目", "   ");

        assertThat(search("任意")).isEmpty();
    }

    @Test
    void unpublished_item_is_removed_from_index() {
        // loader 返回 null 代表条目不可索引（不存在或未发布）。
        // 此时不仅要跳过，还要确保它不在索引里 —— 发布后被编辑成草稿的情况会走到这里。
        service.indexItem(5L, "标题", "曾经的正文。");
        assertThat(search("正文")).isNotEmpty();

        IndexingService withEmptyLoader = new IndexingService(
                vectorStore, new ChunkingService(60, 15), Runnable::run, properties, itemId -> null);
        withEmptyLoader.indexItem(5L);

        assertThat(search("正文")).isEmpty();
    }

    @Test
    void status_reports_item_and_chunk_counts() {
        service.indexItem(1L, "A", "甲的正文。");
        service.indexItem(2L, "B", "乙的正文。");

        IndexStatus status = service.status();

        assertThat(status.enabled()).isTrue();
        assertThat(status.indexedItems()).isEqualTo(2);
        assertThat(status.indexedChunks()).isGreaterThanOrEqualTo(2);
    }

    @Test
    void status_is_disabled_when_rag_off() {
        properties.setEnabled(false);

        IndexStatus status = service.status();

        assertThat(status.enabled()).isFalse();
        assertThat(status.indexedItems()).isZero();
    }

    @Test
    void each_chunk_is_embedded_exactly_once() {
        // 注意：不能断言「总调用次数 == 块数」——removeItem 内部的索引扫描
        // 会额外触发一次查询向量化（SimpleVectorStore 没有按元数据删除的能力，
        // 只能取回全部文档再筛）。这里断言的是正文本身只被向量化一次。
        service.indexItem(1L, "A", "甲的正文。");

        assertThat(embedding.timesEmbedded("甲的正文。")).isEqualTo(1);
    }

    @Test
    void removal_does_not_rely_on_unsupported_filter_delete() {
        // 回归测试：SimpleVectorStore 的 delete(过滤表达式) 会抛
        // UnsupportedOperationException。若实现改回那种写法，
        // 删除会静默失效，重新索引不断累积重复块 —— 这个用例会失败。
        service.indexItem(1L, "A", "甲的正文。");
        service.indexItem(2L, "B", "乙的正文。");

        service.removeItem(1L);

        assertThat(search("正文")).extracting(d -> d.getMetadata().get("itemId"))
                .containsExactly("2");
    }

    @Test
    void rebuild_removes_stale_chunks_that_no_longer_exist_in_the_source() {
        // 回归测试：数据库被重置后条目 id 会变化，只做增量索引会让旧块
        // 永远留在索引里（实测发现过索引中存在已删除条目的块）。
        // 重建必须先清空。
        service.indexItem(999L, "已删除的条目", "这段内容不该留在索引里。");
        assertThat(search("内容")).isNotEmpty();

        IndexingService withOther = new IndexingService(
                vectorStore, new ChunkingService(60, 15), Runnable::run, properties,
                new ItemContentLoader() {
                    @Override
                    public ItemContentLoader.ItemContent load(Long itemId) {
                        return new ItemContentLoader.ItemContent("条目" + itemId, "条目 " + itemId + " 的正文。");
                    }

                    @Override
                    public java.util.Set<Long> allIndexableItemIds() {
                        return java.util.Set.of(1L);
                    }
                });
        withOther.rebuildAll();

        assertThat(search("内容")).extracting(d -> d.getMetadata().get("itemId"))
                .doesNotContain("999");
    }

    @Test
    void rebuild_indexes_every_item_from_the_loader() {
        IndexingService withMany = new IndexingService(
                vectorStore, new ChunkingService(60, 15), Runnable::run, properties,
                new ItemContentLoader() {
                    @Override
                    public ItemContentLoader.ItemContent load(Long itemId) {
                        return new ItemContentLoader.ItemContent("条目" + itemId, "条目 " + itemId + " 的正文。");
                    }

                    @Override
                    public java.util.Set<Long> allIndexableItemIds() {
                        return java.util.Set.of(1L, 2L, 3L);
                    }
                });

        IndexStatus status = withMany.rebuildAll();

        assertThat(status.indexedItems()).isEqualTo(3);
        assertThat(status.lastRebuildAt()).isNotNull();
    }
}
