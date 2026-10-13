package com.njupt.kms.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njupt.kms.knowledge.entity.KnowledgeItem;
import org.apache.ibatis.annotations.Mapper;

/** Task 7 会扩充自定义查询；本任务只需 selectCount 以校验分类是否被引用。 */
@Mapper
public interface KnowledgeItemMapper extends BaseMapper<KnowledgeItem> {
}
