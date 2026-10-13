package com.njupt.kms.ingestion.service;

import com.njupt.kms.ingestion.dto.AttachmentVO;
import com.njupt.kms.ingestion.entity.Attachment;
import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface IngestionService {

    AttachmentVO upload(Long itemId, MultipartFile file);

    List<AttachmentVO> listByItem(Long itemId);

    Attachment get(Long id);

    Resource download(Long id);

    void delete(Long id);
}
