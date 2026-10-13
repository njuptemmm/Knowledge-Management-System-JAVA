package com.njupt.kms.ingestion.parser;

import org.springframework.stereotype.Component;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

@Component
public class MarkdownParser implements TextParser {

    @Override
    public String extension() {
        return "md";
    }

    @Override
    public List<String> extensions() {
        return List.of("md", "markdown", "txt");
    }

    @Override
    public String parse(InputStream input) throws Exception {
        return new String(input.readAllBytes(), StandardCharsets.UTF_8);
    }
}
