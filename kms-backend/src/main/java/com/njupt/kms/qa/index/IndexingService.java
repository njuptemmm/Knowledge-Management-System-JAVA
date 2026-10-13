package com.njupt.kms.qa.index;

import com.njupt.kms.qa.chunk.Chunk;
import com.njupt.kms.qa.chunk.ChunkingService;
import com.njupt.kms.qa.config.RagConfig;
import com.njupt.kms.qa.config.RagProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.SimpleVectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;

import java.io.File;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;

/**
 * 向量索引的构建与维护。
 *
 * <p>只索引 {@code PUBLISHED} 条目（由 {@link ItemContentLoader} 的实现保证）——
 * 草稿与归档内容不进索引，从根源杜绝通过问答泄露未发布内容。
 */
@Slf4j
@Service
public class IndexingService {

    /** 统计查询的取回上限。课程设计的数据规模远小于此值。 */
    private static final int COUNT_PROBE_LIMIT = 2000;

    private final SimpleVectorStore vectorStore;
    private final ChunkingService chunkingService;
    private final Executor embeddingExecutor;
    private final RagProperties properties;
    private final ItemContentLoader contentLoader;

    private volatile LocalDateTime lastRebuildAt;

    public IndexingService(SimpleVectorStore vectorStore,
                           ChunkingService chunkingService,
                           @Qualifier("embeddingExecutor") Executor embeddingExecutor,
                           RagProperties properties,
                           ItemContentLoader contentLoader) {
        this.vectorStore = vectorStore;
        this.chunkingService = chunkingService;
        this.embeddingExecutor = embeddingExecutor;
        this.properties = properties;
        this.contentLoader = contentLoader;
    }

    /** 按 id 重新索引。条目不可索引（不存在或未发布）时，确保它不在索引里。 */
    public void indexItem(Long itemId) {
        if (itemId == null) {
            return;
        }
        ItemContentLoader.ItemContent content = contentLoader.load(itemId);
        if (content == null) {
            log.debug("[RAG] 条目 {} 不可索引（不存在或未发布），已从索引移除", itemId);
            removeItem(itemId);
            return;
        }
        indexItem(itemId, content.title(), content.content());
    }

    /** 重建单个条目的索引（含附件文本）。先清旧块，避免重复累积。 */
    public void indexItem(Long itemId, String title, String content) {
        removeItem(itemId);

        List<Chunk> chunks = chunkingService.split(itemId, content, title);
        if (chunks.isEmpty()) {
            log.debug("[RAG] 条目 {} 无有效内容，跳过索引", itemId);
            persist();
            return;
        }

        List<Document> documents = embedConcurrently(chunks, title);
        if (!documents.isEmpty()) {
            vectorStore.add(documents);
            log.info("[RAG] 条目 {} 索引完成，{} 块", itemId, documents.size());
        }
        persist();
    }

    /**
     * 从索引移除某条目的全部块。
     *
     * <p>**刻意不使用** {@code delete(过滤表达式)}：实测该路径对
     * {@code itemId == 9}（Long）与 {@code itemId == '9'}（String）都不生效，
     * 删除会静默失败，导致重新索引时旧块不断累积。
     * 这里改用只有明确定义的两个 API：取回文档 → 在 Java 侧按 itemId 过滤 → 按 id 删除。
     *
     * <p>代价是每次删除要扫一遍索引（O(n)）。课程设计规模下可忽略；
     * 数据量增长后应改用带原生过滤能力的向量库（见计划「已知限制」）。
     */
    public void removeItem(Long itemId) {
        if (itemId == null) {
            return;
        }
        try {
            String target = String.valueOf(itemId);
            List<String> ids = allDocuments().stream()
                    .filter(document -> target.equals(String.valueOf(document.getMetadata().get("itemId"))))
                    .map(Document::getId)
                    .toList();
            if (!ids.isEmpty()) {
                vectorStore.delete(ids);
                log.debug("[RAG] 条目 {} 移除 {} 个块", itemId, ids.size());
            }
            persist();
        } catch (Exception e) {
            log.warn("[RAG] 移除条目 {} 的索引失败: {}", itemId, e.getMessage());
        }
    }

    /**
     * 并发向量化。
     *
     * <p>火山方舟端点不支持批量且单条约 350ms，串行索引 500 块需 3 分钟。
     * 单块失败不能让整条索引失败——其余块照常入库，失败的记日志。
     */
    private List<Document> embedConcurrently(List<Chunk> chunks, String title) {
        List<CompletableFuture<Document>> futures = chunks.stream()
                .map(chunk -> CompletableFuture.supplyAsync(
                        () -> toDocument(chunk, title), embeddingExecutor))
                .toList();

        List<Document> documents = new ArrayList<>(chunks.size());
        for (int i = 0; i < futures.size(); i++) {
            try {
                documents.add(futures.get(i).join());
            } catch (Exception e) {
                log.warn("[RAG] 条目 {} 第 {} 块向量化失败，已跳过: {}",
                        chunks.get(i).itemId(), i, e.getMessage());
            }
        }
        return documents;
    }

    /**
     * metadata 全部用字符串。数值类型在过滤表达式里会因字面量推断出 Integer
     * 而与存进去的 Long 比较失败（见 {@link #removeItem} 的说明）。
     */
    private Document toDocument(Chunk chunk, String title) {
        return new Document(chunk.docId(), chunk.text(), Map.of(
                "itemId", String.valueOf(chunk.itemId()),
                "title", title == null ? "" : title,
                "headingPath", chunk.headingPath() == null ? "" : chunk.headingPath(),
                "chunkIndex", String.valueOf(chunk.index())));
    }

    /** 全量重建。返回重建后的状态。 */
    public IndexStatus rebuildAll() {
        Set<Long> itemIds = contentLoader.allIndexableItemIds();
        int succeeded = 0;
        int failed = 0;

        for (Long itemId : itemIds) {
            try {
                indexItem(itemId);
                succeeded++;
            } catch (Exception e) {
                failed++;
                log.warn("[RAG] 条目 {} 重建失败: {}", itemId, e.getMessage());
            }
        }

        lastRebuildAt = LocalDateTime.now();
        log.info("[RAG] 全量重建完成：成功 {}，失败 {}，可索引条目 {}",
                succeeded, failed, itemIds.size());
        return status();
    }

    public IndexStatus status() {
        if (!properties.isEnabled()) {
            return IndexStatus.disabled();
        }
        List<Document> documents = allDocuments();
        int items = (int) documents.stream()
                .map(d -> d.getMetadata().get("itemId"))
                .filter(Objects::nonNull)
                .distinct()
                .count();
        return new IndexStatus(true, items, documents.size(), lastRebuildAt);
    }

    /**
     * SimpleVectorStore 不提供计数接口，只能用一个查询把文档取回来数。
     *
     * <p>阈值设为 0 表示不做相似度过滤；topK 取一个足够大的常量。
     * 块数超过 {@value #COUNT_PROBE_LIMIT} 时计数会封顶——这是本实现的已知上限。
     */
    private List<Document> allDocuments() {
        try {
            List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                    .query("统计")
                    .topK(COUNT_PROBE_LIMIT)
                    .similarityThreshold(0.0)
                    .build());
            return documents == null ? List.of() : documents;
        } catch (Exception e) {
            log.debug("[RAG] 统计索引文档失败: {}", e.getMessage());
            return List.of();
        }
    }

    /** 落盘。索引文件是 RAG 唯一的持久化状态，重启后靠它恢复。 */
    private void persist() {
        if (!properties.isEnabled()) {
            return;
        }
        try {
            File file = new File(properties.getVectorStore().getPath());
            RagConfig.ensureParentDirectory(file);
            vectorStore.save(file);
        } catch (Exception e) {
            log.warn("[RAG] 向量索引落盘失败: {}", e.getMessage());
        }
    }
}
