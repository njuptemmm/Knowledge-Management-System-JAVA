package com.njupt.kms.ingestion.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njupt.kms.ingestion.entity.Attachment;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface AttachmentMapper extends BaseMapper<Attachment> {
}
