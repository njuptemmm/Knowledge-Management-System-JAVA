package com.njupt.kms.qa.chunk;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ChunkingServiceTest {

    /** maxLength=60、overlap=15，便于用小段文本构造边界 */
    private final ChunkingService service = new ChunkingService(60, 15);

    @Test
    void empty_or_blank_text_yields_no_chunks() {
        assertThat(service.split(1L, null, "标题")).isEmpty();
        assertThat(service.split(1L, "   ", "标题")).isEmpty();
        assertThat(service.split(1L, "", "标题")).isEmpty();
    }

    @Test
    void short_text_becomes_a_single_chunk() {
        List<Chunk> chunks = service.split(1L, "很短的一段正文。", "标题");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).text()).isEqualTo("很短的一段正文。");
        assertThat(chunks.get(0).index()).isZero();
        assertThat(chunks.get(0).docId()).isEqualTo("item-1-chunk-0");
    }

    @Test
    void splits_on_markdown_headings_keeping_heading_path() {
        String text = """
                # Java 基础

                变量与运算符的说明。

                ## 集合框架

                ArrayList 与 LinkedList 的差异。

                ## 并发

                线程与锁的基础概念。
                """;

        List<Chunk> chunks = service.split(9L, text, "Java 速查");

        assertThat(chunks).hasSize(3);
        assertThat(chunks.get(0).headingPath()).isEqualTo("Java 基础");
        assertThat(chunks.get(1).headingPath()).isEqualTo("Java 基础 > 集合框架");
        assertThat(chunks.get(2).headingPath()).isEqualTo("Java 基础 > 并发");
        assertThat(chunks.get(1).text()).contains("ArrayList").doesNotContain("线程与锁");
    }

    @Test
    void text_without_headings_uses_document_title_as_path() {
        List<Chunk> chunks = service.split(3L, "没有标题结构的纯文本内容。", "需求分析");

        assertThat(chunks).hasSize(1);
        assertThat(chunks.get(0).headingPath()).isEqualTo("需求分析");
    }

    @Test
    void long_section_is_split_with_overlap() {
        // 连续无标点文本，迫使走硬切分支
        String longText = "0123456789".repeat(15);

        List<Chunk> chunks = service.split(1L, longText, "T");

        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks).allSatisfy(c -> assertThat(c.text().length()).isLessThanOrEqualTo(60));
        // 相邻块必须有重叠，否则语义会在切点处断裂
        for (int i = 1; i < chunks.size(); i++) {
            String previous = chunks.get(i - 1).text();
            String overlap = previous.substring(Math.max(0, previous.length() - 15));
            assertThat(chunks.get(i).text()).startsWith(overlap);
        }
    }

    @Test
    void chunk_indexes_are_sequential_across_sections() {
        String text = "# A\n\n" + "甲".repeat(200) + "\n\n# B\n\n较短的内容。";

        List<Chunk> chunks = service.split(7L, text, "文档");

        for (int i = 0; i < chunks.size(); i++) {
            assertThat(chunks.get(i).index()).isEqualTo(i);
            assertThat(chunks.get(i).itemId()).isEqualTo(7L);
            assertThat(chunks.get(i).docId()).isEqualTo("item-7-chunk-" + i);
        }
    }

    @Test
    void deep_heading_levels_are_flattened_into_a_path() {
        String text = """
                # 一级

                ## 二级

                ### 三级

                最深的内容。
                """;

        List<Chunk> chunks = service.split(1L, text, "T");

        assertThat(chunks).anySatisfy(c ->
                assertThat(c.headingPath()).isEqualTo("一级 > 二级 > 三级"));
    }

    @Test
    void heading_level_reset_truncates_the_path() {
        // # A / ## B / # C 时，C 的路径不能是 A > B > C
        String text = """
                # 一级

                ## 二级

                内容 A

                # 另一个一级

                内容 B
                """;

        List<Chunk> chunks = service.split(1L, text, "T");

        assertThat(chunks).anySatisfy(c ->
                assertThat(c.headingPath()).isEqualTo("一级 > 二级"));
        assertThat(chunks).anySatisfy(c ->
                assertThat(c.headingPath()).isEqualTo("另一个一级"));
        assertThat(chunks.stream().map(Chunk::headingPath).toList())
                .doesNotContain("一级 > 二级 > 另一个一级");
    }

    @Test
    void heading_without_body_contributes_no_empty_chunk() {
        String text = """
                # 空标题

                ## 有内容

                正文在这里。
                """;

        List<Chunk> chunks = service.split(1L, text, "T");

        assertThat(chunks).allSatisfy(c -> assertThat(c.text()).isNotBlank());
        assertThat(chunks).hasSize(1);
    }

    @Test
    void overlap_is_clamped_so_it_cannot_exceed_max_length() {
        // 传一个荒谬的 overlap，服务不能因此死循环
        ChunkingService weird = new ChunkingService(20, 999);

        List<Chunk> chunks = weird.split(1L, "内容".repeat(100), "T");

        assertThat(chunks).isNotEmpty();
        assertThat(chunks).allSatisfy(c -> assertThat(c.text().length()).isLessThanOrEqualTo(20));
    }

    @Test
    void breaks_at_sentence_boundary_when_possible() {
        // 在标点处断句比在字中间硬切更自然
        String text = "第一句话结束了。第二句话也结束了。第三句话同样结束了。第四句继续补充内容。";

        ChunkingService small = new ChunkingService(30, 5);
        List<Chunk> chunks = small.split(1L, text, "T");

        assertThat(chunks.size()).isGreaterThan(1);
        assertThat(chunks.get(0).text()).endsWith("。");
    }
}
