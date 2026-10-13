package com.njupt.kms.ingestion.parser;

import com.njupt.kms.common.BusinessException;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

@Component
public class ParserFactory {

    private final Map<String, TextParser> parsers = new HashMap<>();

    public ParserFactory(List<TextParser> available) {
        for (TextParser parser : available) {
            for (String extension : parser.extensions()) {
                parsers.put(extension.toLowerCase(Locale.ROOT), parser);
            }
        }
    }

    /** 按文件名（可含路径）解析出对应解析器。 */
    public TextParser forFilename(String filename) {
        String extension = extensionOf(filename);
        TextParser parser = parsers.get(extension);
        if (parser == null) {
            throw new BusinessException("不支持的文件类型：" + (extension.isEmpty() ? "未知" : extension));
        }
        return parser;
    }

    private String extensionOf(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "";
        }
        int dot = filename.lastIndexOf('.');
        if (dot < 0 || dot == filename.length() - 1) {
            return "";
        }
        return filename.substring(dot + 1).toLowerCase(Locale.ROOT);
    }
}
