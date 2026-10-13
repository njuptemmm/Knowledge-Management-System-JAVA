package com.njupt.kms.ingestion.dto;

import com.njupt.kms.ingestion.entity.Attachment;
import lombok.Data;

import java.time.LocalDateTime;

@Data
public class AttachmentVO {

    private static final int PREVIEW_LENGTH = 200;

    private Long id;
    private Long itemId;
    private String originalName;
    private String mimeType;
    private Long sizeBytes;
    private String parseStatus;
    private String parseError;
    private String textPreview;
    private LocalDateTime createdAt;

    public static AttachmentVO from(Attachment attachment) {
        AttachmentVO vo = new AttachmentVO();
        vo.setId(attachment.getId());
        vo.setItemId(attachment.getItemId());
        vo.setOriginalName(attachment.getOriginalName());
        vo.setMimeType(attachment.getMimeType());
        vo.setSizeBytes(attachment.getSizeBytes());
        vo.setParseStatus(attachment.getParseStatus());
        vo.setParseError(attachment.getParseError());
        vo.setCreatedAt(attachment.getCreatedAt());

        String text = attachment.getParsedText();
        if (text != null && !text.isBlank()) {
            vo.setTextPreview(text.length() <= PREVIEW_LENGTH
                    ? text
                    : text.substring(0, PREVIEW_LENGTH) + "…");
        }
        return vo;
    }
}
