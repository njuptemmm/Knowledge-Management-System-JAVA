package com.njupt.kms.qa.retrieve;

import com.njupt.kms.common.BusinessException;
import com.njupt.kms.knowledge.service.KnowledgeSearchPort;
import com.njupt.kms.qa.config.RagProperties;
import com.njupt.kms.security.CurrentUser;
import com.njupt.kms.security.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 向量召回。
 *
 * <p>召回后必须按**当前真实可见性**二次过滤：索引与状态变更之间存在时间窗，
 * 只信索引里的 metadata 会有窗口期漏出已归档内容。可见性规则由 M1 的
 * {@link KnowledgeSearchPort#visibleItemIds} 提供，与列表、检索接口完全一致。
 */
@Slf4j
@Service
public class RetrievalService {

    /** 超采倍数：先多召回一些，过滤与去重之后再截断到 topK。 */
    private static final int OVER_FETCH_FACTOR = 3;

    /**
     * 单篇文档最多贡献几块。
     *
     * <p>不限制的话，一篇长文档会占满 prompt 预算，挤掉其他来源；
     * 但限制为 1 又太紧——同一篇文档的不同小节常常共同回答一个问题
     * （比如「聚簇索引和非聚簇索引有什么区别」需要两节的内容），
     * 只放行得分最高的那块会导致明明检索到了却答不出来。
     */
    private static final int MAX_CHUNKS_PER_ITEM = 3;

    private final VectorStore vectorStore;
    private final RagProperties properties;
    private final KnowledgeSearchPort knowledgeSearchPort;

    public RetrievalService(VectorStore vectorStore,
                            RagProperties properties,
                            KnowledgeSearchPort knowledgeSearchPort) {
        this.vectorStore = vectorStore;
        this.properties = properties;
        this.knowledgeSearchPort = knowledgeSearchPort;
    }

    /**
     * 召回与问题相关的块，按相关度降序。
     *
     * <p>当前用户从 {@link CurrentUser} 读取——召回发生在请求线程上
     * （流式响应开始之前），因此 ThreadLocal 可用。
     */
    public List<RetrievedChunk> retrieve(String question) {
        if (!StringUtils.hasText(question)) {
            throw new BusinessException("问题不能为空");
        }
        if (!properties.isEnabled()) {
            throw new BusinessException(
                    "问答功能未启用：请在 application-local.yaml 中配置 app.rag.*");
        }

        String normalized = question.strip();
        List<RetrievedChunk> candidates = vectorSearch(
                normalized, properties.getRetrieve().getTopK() * OVER_FETCH_FACTOR);
        if (candidates.isEmpty()) {
            return List.of();
        }

        User current = CurrentUser.get();
        Set<Long> visible = knowledgeSearchPort.visibleItemIds(
                candidates.stream().map(RetrievedChunk::itemId).distinct().toList(),
                current == null ? null : current.getId());
        if (visible == null) {
            visible = Set.of();
        }

        // 先按得分降序，保证每篇文档留下的是最相关的几块
        List<RetrievedChunk> usable = new ArrayList<>(candidates);
        usable.sort((a, b) -> Double.compare(b.score(), a.score()));

        Map<Long, Integer> perItemCount = new HashMap<>();
        List<RetrievedChunk> result = new ArrayList<>();
        for (RetrievedChunk candidate : usable) {
            if (candidate.score() < properties.getRetrieve().getMinScore()) {
                log.debug("[RAG] 丢弃低相关块 itemId={} score={}", candidate.itemId(), candidate.score());
                continue;
            }
            if (!visible.contains(candidate.itemId())) {
                log.debug("[RAG] 丢弃当前不可见条目的召回块 itemId={}", candidate.itemId());
                continue;
            }
            int used = perItemCount.getOrDefault(candidate.itemId(), 0);
            if (used >= MAX_CHUNKS_PER_ITEM) {
                continue;
            }
            perItemCount.put(candidate.itemId(), used + 1);
            result.add(candidate);
        }

        int topK = properties.getRetrieve().getTopK();
        return result.size() > topK ? result.subList(0, topK) : result;
    }

    /** 真正的向量查询。抽成 protected 便于测试替换掉向量库。 */
    protected List<RetrievedChunk> vectorSearch(String question, int topK) {
        List<Document> documents = vectorStore.similaritySearch(SearchRequest.builder()
                .query(question)
                .topK(Math.max(1, topK))
                .similarityThreshold(0.0)
                .build());
        if (documents == null) {
            return List.of();
        }

        List<RetrievedChunk> chunks = new ArrayList<>(documents.size());
        for (Document document : documents) {
            Map<String, Object> metadata = document.getMetadata();
            Object rawItemId = metadata.get("itemId");
            if (rawItemId == null) {
                continue;
            }
            Long itemId;
            try {
                itemId = Long.valueOf(String.valueOf(rawItemId));
            } catch (NumberFormatException e) {
                log.warn("[RAG] 文档 {} 的 itemId 非法，已跳过", document.getId());
                continue;
            }
            chunks.add(new RetrievedChunk(
                    itemId,
                    String.valueOf(metadata.getOrDefault("title", "")),
                    String.valueOf(metadata.getOrDefault("headingPath", "")),
                    document.getText(),
                    document.getScore() == null ? 0.0 : document.getScore()));
        }
        return chunks;
    }
}
