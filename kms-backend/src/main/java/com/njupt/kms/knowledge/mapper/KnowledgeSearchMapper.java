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
}
