package com.njupt.kms.ingestion.event;

/** 附件落盘完成。发布后由 AttachmentParseListener 异步消费并抽取文本。 */
public record AttachmentUploadedEvent(Long attachmentId) {
}
