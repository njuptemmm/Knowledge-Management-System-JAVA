package com.njupt.kms.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.njupt.kms.knowledge.dto.KnowledgeListVO;
import com.njupt.kms.knowledge.entity.KnowledgeItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.util.Collection;

@Mapper
public interface KnowledgeItemMapper extends BaseMapper<KnowledgeItem> {

    /** 浏览数自增。用 SQL 原子自增而非「查-加-写」，避免并发丢失更新。 */
    @Update("UPDATE knowledge_item SET view_count = view_count + 1 WHERE id = #{id}")
    int incrementViewCount(@Param("id") Long id);

    /**
     * 列表查询。visibleAuthorId 为 null 时只返回 PUBLISHED；
     * 非 null 时返回「PUBLISHED 或该作者自己的条目」（即含自己的草稿与归档）。
     * categoryIds 为空时不限制分类。
     */
    IPage<KnowledgeListVO> selectPageVO(IPage<KnowledgeListVO> page,
                                        @Param("categoryIds") Collection<Long> categoryIds,
                                        @Param("status") String status,
                                        @Param("tagId") Long tagId,
                                        @Param("visibleAuthorId") Long visibleAuthorId);
}
