package com.njupt.kms.qa.index;

import java.time.LocalDateTime;

/**
 * 索引状态快照。
 *
 * @param enabled       问答功能是否启用
 * @param indexedItems  已索引的条目数
 * @param indexedChunks 已索引的块数
 * @param lastRebuildAt 上次全量重建时间，本次进程内从未重建过则为 null
 */
public record IndexStatus(boolean enabled, int indexedItems, int indexedChunks, LocalDateTime lastRebuildAt) {

    public static IndexStatus disabled() {
        return new IndexStatus(false, 0, 0, null);
    }
}
