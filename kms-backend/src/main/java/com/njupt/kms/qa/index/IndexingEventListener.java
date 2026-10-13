package com.njupt.kms.qa.index;

import com.njupt.kms.common.event.KnowledgeIndexEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 条目变更后异步重建索引。
 *
 * <p>复用 M2 附件解析同一套机制：事务提交后才执行，避免读到未提交的数据；
 * 整体吞掉异常，索引失败绝不能反过来让发布/编辑操作失败。
 *
 * <p>用的是独立的 {@code ragIndexExecutor} 而非 {@code embeddingExecutor}：
 * 本方法会把分块提交给后者再阻塞等待，共用同一个池会导致互相等待。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class IndexingEventListener {

    private final IndexingService indexingService;

    @Async("ragIndexExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onKnowledgeChanged(KnowledgeIndexEvent event) {
        if (event == null || event.itemId() == null) {
            return;
        }
        try {
            if (event.action() == KnowledgeIndexEvent.Action.REMOVE) {
                indexingService.removeItem(event.itemId());
            } else {
                indexingService.indexItem(event.itemId());
            }
        } catch (Exception e) {
            log.error("[RAG] 条目 {} 索引更新失败，不影响主流程", event.itemId(), e);
        }
    }
}
