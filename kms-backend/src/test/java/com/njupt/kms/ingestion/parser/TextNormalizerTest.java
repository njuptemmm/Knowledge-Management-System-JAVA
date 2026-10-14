package com.njupt.kms.ingestion.parser;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextNormalizerTest {

    @Test
    void null_and_empty_pass_through() {
        assertThat(TextNormalizer.normalizeCompatibilityCharacters(null)).isNull();
        assertThat(TextNormalizer.normalizeCompatibilityCharacters("")).isEmpty();
    }

    @Test
    void kangxi_radicals_become_standard_han_characters() {
        // 这五个字符取自真实 PDF 的抽取结果（课程考试要求.pdf）：
        // 肉眼是「工无小心目」，实际码点在康熙部首区 U+2F00–U+2FDF
        String extracted = "⼯程 ⽆ ⼩组 ⼼ ⽬的";
        String expected = "工程 无 小组 心 目的";

        assertThat(TextNormalizer.normalizeCompatibilityCharacters(extracted)).isEqualTo(expected);
    }

    @Test
    void text_containing_radicals_becomes_searchable_by_normal_characters() {
        String extracted = "软件⼯程（双语）考察要求";

        String normalized = TextNormalizer.normalizeCompatibilityCharacters(extracted);

        assertThat(extracted).doesNotContain("工程");
        assertThat(normalized).contains("工程");
    }

    @Test
    void cjk_compatibility_ideographs_are_normalized() {
        // U+F900–U+FAFF 兼容表意文字，字形与标准汉字相同但码点不同
        assertThat(TextNormalizer.normalizeCompatibilityCharacters("更新"))
                .isEqualTo("更新");
        assertThat(TextNormalizer.normalizeCompatibilityCharacters("車輛"))
                .isEqualTo("車輛");
    }

    @Test
    void chinese_full_width_punctuation_is_left_alone() {
        // 整体 NFKC 会把「，。」转成 ASCII，破坏中文排版，因此必须逐字符处理
        String chinese = "第一，第二。第三；第四！第五？";

        assertThat(TextNormalizer.normalizeCompatibilityCharacters(chinese)).isEqualTo(chinese);
    }

    @Test
    void full_width_latin_is_left_alone() {
        // 全角字母数字同样不转换
        String fullWidth = "ＡＢＣ１２３";

        assertThat(TextNormalizer.normalizeCompatibilityCharacters(fullWidth)).isEqualTo(fullWidth);
    }

    @Test
    void ordinary_text_is_returned_unchanged() {
        String plain = "普通的中文与 English 123 混排，含标点：冒号、分号。";

        assertThat(TextNormalizer.normalizeCompatibilityCharacters(plain)).isEqualTo(plain);
    }

    @Test
    void mixed_text_keeps_everything_else_intact() {
        String mixed = "⼩组作业质量重要，占 40%。";

        assertThat(TextNormalizer.normalizeCompatibilityCharacters(mixed))
                .isEqualTo("小组作业质量重要，占 40%。");
    }
}
