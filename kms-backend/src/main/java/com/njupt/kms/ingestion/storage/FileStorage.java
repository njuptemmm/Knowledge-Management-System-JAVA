package com.njupt.kms.ingestion.storage;

import org.springframework.core.io.Resource;
import org.springframework.web.multipart.MultipartFile;

public interface FileStorage {

    /** 落盘并返回相对存储路径（相对上传根目录）。 */
    String store(MultipartFile file);

    Resource load(String storedPath);

    void delete(String storedPath);
}
