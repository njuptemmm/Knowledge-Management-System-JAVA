package com.njupt.kms.ingestion.listener;

import com.njupt.kms.ingestion.entity.Attachment;
import com.njupt.kms.ingestion.event.AttachmentUploadedEvent;
import com.njupt.kms.ingestion.mapper.AttachmentMapper;
import com.njupt.kms.ingestion.parser.ParserFactory;
import com.njupt.kms.ingestion.parser.TextNormalizer;
import com.njupt.kms.ingestion.parser.TextParser;
import com.njupt.kms.ingestion.storage.FileStorage;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.io.InputStream;

@Slf4j
@Component
@RequiredArgsConstructor
public class AttachmentParseListener {

    private static final int MAX_ERROR_LENGTH = 500;

    private final AttachmentMapper attachmentMapper;
    private final FileStorage fileStorage;
    private final ParserFactory parserFactory;

    /**
     * 事务提交后才解析，避免异步线程读到尚未提交的附件记录。
     *
     * <p>整体 try-catch：解析失败绝不能冒泡——它已在独立线程中，
     * 抛出只会丢失错误信息，且不应影响上传本身的成功。
     */
    @Async("attachmentParseExecutor")
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onUploaded(AttachmentUploadedEvent event) {
        Long id = event.attachmentId();
        Attachment attachment = attachmentMapper.selectById(id);
        if (attachment == null) {
            log.warn("附件记录不存在，跳过解析 id={}", id);
            return;
        }

        markStatus(attachment, Attachment.STATUS_PARSING, null);

        try {
            TextParser parser = parserFactory.forFilename(attachment.getOriginalName());
            String text;
            try (InputStream in = fileStorage.load(attachment.getStoredPath()).getInputStream()) {
                text = parser.parse(in);
            }
            // PDF 内嵌字体常把汉字映射成康熙部首等兼容码点，不归一化就搜不到
            text = TextNormalizer.normalizeCompatibilityCharacters(text);

            attachment.setParsedText(text);
            markStatus(attachment, Attachment.STATUS_DONE, null);
            log.info("附件解析完成 id={} 字符数={}", id, text == null ? 0 : text.length());

        } catch (Exception e) {
            log.error("附件解析失败 id={} name={}", id, attachment.getOriginalName(), e);
            markStatus(attachment, Attachment.STATUS_FAILED, truncate(e.getMessage()));
        }
    }

    private void markStatus(Attachment attachment, String status, String error) {
        attachment.setParseStatus(status);
        attachment.setParseError(error);
        attachmentMapper.updateById(attachment);
    }

    /** parse_error 列宽 500，超出会插入失败，故先截断。 */
    private String truncate(String message) {
        if (message == null) {
            return "解析失败";
        }
        return message.length() <= MAX_ERROR_LENGTH ? message : message.substring(0, MAX_ERROR_LENGTH);
    }
}
