package com.njupt.kms.ingestion.parser;

import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.util.List;

/**
 * 图片解析器：v0.0.1 不做 OCR，直接返回空文本并标记解析完成。
 *
 * <p>存在此实现的意义是让图片走完整的解析状态机（PENDING → DONE），
 * 前端展示一致；后续接入 OCR 只需改动本类。
 */
@Component
public class ImageParser implements TextParser {

    @Override
    public String extension() {
        return "png";
    }

    @Override
    public List<String> extensions() {
        return List.of("png", "jpg", "jpeg", "gif", "webp");
    }

    @Override
    public String parse(InputStream input) {
        return "";
    }
}
