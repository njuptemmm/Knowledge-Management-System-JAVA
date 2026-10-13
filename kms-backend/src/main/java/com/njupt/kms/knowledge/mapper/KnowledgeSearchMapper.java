package com.njupt.kms.knowledge.mapper;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njupt.kms.knowledge.dto.KnowledgeSearchRow;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Map;

@Mapper
public interface KnowledgeSearchMapper {

    IPage<KnowledgeSearchRow> search(IPage<KnowledgeSearchRow> page,
                                     @Param("kw") String keyword,
                                     @Param("wTitle") double wTitle,
                                     @Param("wSummary") double wSummary,
                                     @Param("wContent") double wContent,
                                     @Param("categoryIds") Collection<Long> categoryIds,
                                     @Param("tagId") Long tagId,
                                     @Param("contentType") String contentType,
                                     @Param("status") String status,
                                     @Param("from") LocalDate from,
                                     @Param("to") LocalDate to,
                                     @Param("sort") String sort,
                                     @Param("visibleAuthorId") Long visibleAuthorId,
                                     @Param("useFullText") boolean useFullText);

    List<Map<String, Object>> selectTagsOf(@Param("itemIds") Collection<Long> itemIds);

    /**
     * 批量取「当前真正可见」的条目 id（设计文档 §3.3 的受控例外，同 {@code search}）。
     *
     * <p>供 M5 问答域在向量召回后做可见性二次过滤使用：
     * 索引与状态变更之间存在窗口期，只信索引 metadata 会漏出已归档内容。
     *
     * @param currentUserId 为 null 时只返回 PUBLISHED 的条目
     */
    List<Long> selectVisibleIds(@Param("itemIds") Collection<Long> itemIds,
                                @Param("currentUserId") Long currentUserId);

    /** 单个已发布条目的标题与完整正文，供 RAG 索引使用。 */
    Map<String, Object> selectPublishedById(@Param("itemId") Long itemId);

    /** 全部已发布条目的 id。 */
    List<Long> selectPublishedIds();
}
