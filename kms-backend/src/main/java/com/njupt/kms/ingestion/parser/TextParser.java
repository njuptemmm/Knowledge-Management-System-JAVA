package com.njupt.kms.ingestion.parser;

import java.io.InputStream;
import java.util.List;

/** 文件文本抽取器。每种支持的扩展名对应一个实现。 */
public interface TextParser {

    /** 主扩展名（小写，不含点），如 "pdf"。 */
    String extension();

    /** 该解析器支持的全部扩展名。默认只有 extension() 一个。 */
    default List<String> extensions() {
        return List.of(extension());
    }

    /** 抽取纯文本。失败时抛异常，由调用方记录到 parse_error。 */
    String parse(InputStream input) throws Exception;
}
