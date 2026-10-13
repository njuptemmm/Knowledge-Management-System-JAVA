package com.njupt.kms.ingestion;

import com.njupt.kms.common.BusinessException;
import com.njupt.kms.ingestion.parser.DocxParser;
import com.njupt.kms.ingestion.parser.ImageParser;
import com.njupt.kms.ingestion.parser.MarkdownParser;
import com.njupt.kms.ingestion.parser.ParserFactory;
import com.njupt.kms.ingestion.parser.PdfParser;
import com.njupt.kms.ingestion.parser.TextParser;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ParserFactoryTest {

    private final ParserFactory factory = new ParserFactory(List.of(
            new PdfParser(), new DocxParser(), new MarkdownParser(), new ImageParser()));

    private ByteArrayInputStream text(String content) {
        return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void resolves_parser_by_extension_ignoring_case() {
        assertThat(factory.forFilename("报告.PDF")).isInstanceOf(PdfParser.class);
        assertThat(factory.forFilename("notes.md")).isInstanceOf(MarkdownParser.class);
        assertThat(factory.forFilename("a.docx")).isInstanceOf(DocxParser.class);
        assertThat(factory.forFilename("p.png")).isInstanceOf(ImageParser.class);
    }

    @Test
    void resolves_full_path_filename() {
        assertThat(factory.forFilename("uploads/202610/abc123.pdf")).isInstanceOf(PdfParser.class);
    }

    @Test
    void markdown_parser_also_handles_markdown_and_txt() {
        assertThat(factory.forFilename("a.markdown")).isInstanceOf(MarkdownParser.class);
        assertThat(factory.forFilename("a.txt")).isInstanceOf(MarkdownParser.class);
    }

    @Test
    void image_parser_handles_common_formats() {
        for (String ext : List.of("png", "jpg", "jpeg", "gif", "webp")) {
            assertThat(factory.forFilename("photo." + ext)).isInstanceOf(ImageParser.class);
        }
    }

    @Test
    void rejects_unknown_extension() {
        assertThatThrownBy(() -> factory.forFilename("archive.zip"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的文件类型");
    }

    @Test
    void legacy_doc_has_no_parser_but_gives_a_named_error() {
        // .doc 可以上传存储，但解析不了。错误信息必须点出扩展名，
        // 否则用户看到「解析失败」不知道是格式问题还是文件损坏。
        assertThatThrownBy(() -> factory.forFilename("old.doc"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的文件类型：doc");
    }

    @Test
    void rejects_null_or_extensionless_filename() {
        assertThatThrownBy(() -> factory.forFilename(null))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> factory.forFilename("noextension"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> factory.forFilename("trailingdot."))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void markdown_parser_reads_plain_text() throws Exception {
        TextParser parser = factory.forFilename("a.md");

        String parsed = parser.parse(text("# 标题\n\n正文内容"));

        assertThat(parsed).contains("标题").contains("正文内容");
    }

    @Test
    void image_parser_returns_empty_text_without_failing() throws Exception {
        TextParser parser = factory.forFilename("a.png");

        assertThat(parser.parse(text("not really an image"))).isEmpty();
    }
}
