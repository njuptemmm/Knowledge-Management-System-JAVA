package com.njupt.kms.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njupt.kms.knowledge.entity.KnowledgeItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface KnowledgeItemMapper extends BaseMapper<KnowledgeItem> {

    /** 浏览数自增。用 SQL 原子自增而非「查-加-写」，避免并发丢失更新。 */
    @Update("UPDATE knowledge_item SET view_count = view_count + 1 WHERE id = #{id}")
    int incrementViewCount(@Param("id") Long id);
}
