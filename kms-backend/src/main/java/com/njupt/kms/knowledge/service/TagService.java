package com.njupt.kms.knowledge.service;

import java.util.List;

public interface TagService {

    /** 覆盖式替换某条目的标签。传空列表表示清空。 */
    void replaceTags(Long itemId, List<Long> tagIds);
}
