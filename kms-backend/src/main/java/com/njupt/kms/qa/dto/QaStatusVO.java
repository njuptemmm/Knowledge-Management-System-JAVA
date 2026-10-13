package com.njupt.kms.qa.dto;

import java.time.LocalDateTime;

/**
 * 索引状态。
 *
 * @param enabled       问答功能是否启用（未配置密钥时为 false）
 * @param indexedItems  已索引的条目数
 * @param indexedChunks 已索引的块数
 * @param lastRebuildAt 本次进程内上次全量重建时间，未重建过为 null
 */
public record QaStatusVO(boolean enabled, int indexedItems, int indexedChunks, LocalDateTime lastRebuildAt) {
}
