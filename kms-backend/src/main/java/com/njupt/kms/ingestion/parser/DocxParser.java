package com.njupt.kms.ingestion.parser;

import org.apache.poi.xwpf.extractor.XWPFWordExtractor;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/**
 * 只声明 docx。
 *
 * <p>旧版 {@code .doc}（OLE2 复合文档）需要 poi-scratchpad 才能读取，且抽取质量差。
 * v0.0.1 不支持它：文件可以上传与下载，但注册的扩展名里没有 doc，
 * 因此解析阶段会得到明确的「不支持的文件类型：doc」，而不是一个含混的失败。
 */
@Component
public class DocxParser implements TextParser {

    @Override
    public String extension() {
        return "docx";
    }

    @Override
    public String parse(InputStream input) throws Exception {
        try (XWPFDocument document = new XWPFDocument(input);
             XWPFWordExtractor extractor = new XWPFWordExtractor(document)) {
            return extractor.getText();
        }
    }
}
