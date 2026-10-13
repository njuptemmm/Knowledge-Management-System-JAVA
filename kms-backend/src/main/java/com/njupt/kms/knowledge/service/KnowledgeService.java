package com.njupt.kms.knowledge.service;

import com.njupt.kms.knowledge.dto.KnowledgeDetailVO;
import com.njupt.kms.knowledge.dto.KnowledgeSaveRequest;
import com.njupt.kms.knowledge.entity.KnowledgeItem;

public interface KnowledgeService {

    KnowledgeDetailVO create(KnowledgeSaveRequest request);

    KnowledgeDetailVO update(Long id, KnowledgeSaveRequest request);

    /** 读取详情，并累加浏览数。 */
    KnowledgeDetailVO detail(Long id);

    void delete(Long id);

    /** 供内部与他模块使用的实体读取，不累加浏览数。 */
    KnowledgeItem getEntity(Long id);
}
