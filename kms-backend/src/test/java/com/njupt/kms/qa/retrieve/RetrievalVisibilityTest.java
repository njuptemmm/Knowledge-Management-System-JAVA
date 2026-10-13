package com.njupt.kms.qa.retrieve;

import com.njupt.kms.knowledge.dto.KnowledgeSearchCondition;
import com.njupt.kms.knowledge.dto.KnowledgeSearchRow;
import com.njupt.kms.knowledge.service.KnowledgeSearchPort;
import com.njupt.kms.qa.config.RagProperties;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RetrievalVisibilityTest {

    /** 只把指定 id 视为可见的假端口。 */
    static class FakePort implements KnowledgeSearchPort {
        final Set<Long> visible;

        FakePort(Set<Long> visible) {
            this.visible = visible;
        }

        @Override
        public IPage<KnowledgeSearchRow> search(KnowledgeSearchCondition condition, long page, long size) {
            return new Page<>();
        }

        @Override
        public Map<Long, List<String>> tagNamesOf(Collection<Long> itemIds) {
            return Map.of();
        }

        @Override
        public Set<Long> visibleItemIds(Collection<Long> itemIds, Long currentUserId) {
            return visible;
        }
    }

    private RagProperties properties;

    @BeforeEach
    void setUp() {
        properties = new RagProperties();
        properties.setEnabled(true);
        properties.getRetrieve().setTopK(3);
        properties.getRetrieve().setMinScore(0.3);
    }

    private RetrievedChunk chunk(long itemId, double score) {
        return new RetrievedChunk(itemId, "标题" + itemId, "路径", "正文" + itemId, score);
    }

    /** 固定返回给定候选块、绕开真实向量库的检索器。 */
    private RetrievalService service(List<RetrievedChunk> candidates, Set<Long> visible) {
        return new RetrievalService(null, properties, new FakePort(visible)) {
            @Override
            protected List<RetrievedChunk> vectorSearch(String question, int topK) {
                return candidates;
            }
        };
    }

    @Test
    void drops_candidates_whose_item_is_not_visible_now() {
        // 索引与状态变更之间有窗口期，只靠索引 metadata 会漏出已归档内容，
        // 因此必须按当前真实可见性二次过滤
        RetrievalService retrieval = service(
                List.of(chunk(1, 0.9), chunk(2, 0.8), chunk(3, 0.7)),
                Set.of(1L, 3L));

        assertThat(retrieval.retrieve("问题"))
                .extracting(RetrievedChunk::itemId)
                .containsExactly(1L, 3L);
    }

    @Test
    void drops_candidates_below_min_score() {
        RetrievalService retrieval = service(
                List.of(chunk(1, 0.9), chunk(2, 0.2), chunk(3, 0.35)),
                Set.of(1L, 2L, 3L));

        assertThat(retrieval.retrieve("问题"))
                .extracting(RetrievedChunk::itemId)
                .containsExactly(1L, 3L);
    }

    @Test
    void caps_results_at_top_k() {
        RetrievalService retrieval = service(
                List.of(chunk(1, 0.9), chunk(2, 0.8), chunk(3, 0.7), chunk(4, 0.6)),
                Set.of(1L, 2L, 3L, 4L));

        assertThat(retrieval.retrieve("问题")).hasSize(3);
    }

    @Test
    void keeps_multiple_chunks_from_one_document_best_first() {
        // 同一篇文档的不同小节常常共同回答一个问题
        // （如「聚簇索引和非聚簇索引的区别」需要两节），因此不能只留一块
        properties.getRetrieve().setTopK(6);
        RetrievalService retrieval = service(
                List.of(chunk(1, 0.9), chunk(1, 0.6), chunk(2, 0.7)),
                Set.of(1L, 2L));

        List<RetrievedChunk> result = retrieval.retrieve("问题");

        // 全局按分数降序，因此同一篇文档的两块不必相邻
        assertThat(result).extracting(RetrievedChunk::score).containsExactly(0.9, 0.7, 0.6);
        assertThat(result).extracting(RetrievedChunk::itemId).containsExactly(1L, 2L, 1L);
    }

    @Test
    void one_document_cannot_take_more_than_the_per_item_cap() {
        // 单篇文档最多 3 块，其余位置留给别的来源
        properties.getRetrieve().setTopK(6);
        RetrievalService retrieval = service(
                List.of(chunk(1, 0.9), chunk(1, 0.85), chunk(1, 0.8), chunk(1, 0.75),
                        chunk(2, 0.7), chunk(3, 0.6)),
                Set.of(1L, 2L, 3L));

        List<RetrievedChunk> result = retrieval.retrieve("问题");

        assertThat(result).filteredOn(c -> c.itemId() == 1L).hasSize(3);
        assertThat(result).extracting(RetrievedChunk::itemId).contains(2L, 3L);
        // 被挤掉的是同一篇文档里得分最低的那块
        assertThat(result).extracting(RetrievedChunk::score).doesNotContain(0.75);
    }

    @Test
    void results_are_sorted_by_score_descending() {
        RetrievalService retrieval = service(
                List.of(chunk(1, 0.5), chunk(2, 0.9), chunk(3, 0.7)),
                Set.of(1L, 2L, 3L));

        assertThat(retrieval.retrieve("问题"))
                .extracting(RetrievedChunk::score)
                .containsExactly(0.9, 0.7, 0.5);
    }

    @Test
    void rejects_empty_question() {
        RetrievalService retrieval = service(List.of(), Set.of());

        assertThatThrownBy(() -> retrieval.retrieve("  "))
                .hasMessageContaining("问题不能为空");
    }

    @Test
    void disabled_rag_gives_a_readable_message_not_a_crash() {
        RagProperties off = new RagProperties();   // enabled 默认 false
        RetrievalService retrieval = new RetrievalService(null, off, new FakePort(Set.of()));

        assertThatThrownBy(() -> retrieval.retrieve("任意问题"))
                .hasMessageContaining("问答功能未启用")
                .hasMessageContaining("application-local.yaml");
    }

    @Test
    void empty_candidates_yield_empty_result_without_querying_visibility() {
        RetrievalService retrieval = service(List.of(), Set.of(1L));

        assertThat(retrieval.retrieve("问题")).isEmpty();
    }
}
