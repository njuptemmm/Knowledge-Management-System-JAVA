package com.njupt.kms.ingestion.parser;

import java.text.Normalizer;

/**
 * 抽取文本的字符归一化。
 *
 * <p>PDF 的内嵌字体经常把部分汉字映射到**兼容码点**，最常见的是康熙部首：
 * 实测一份中文 PDF 抽取出的「工」「无」「小」「目」分别是
 * U+2F2F、U+2F46、U+2F29、U+2F6C（康熙部首区），而不是
 * U+5DE5、U+65E0、U+5C0F、U+76EE（标准汉字）。
 *
 * <p>两者字形相同、码点不同，肉眼与截图都看不出差异，但会导致：
 * 搜索「工程」匹配不到「⼯程」；这些文本若进入向量索引，
 * 相关查询会完全检索不到。用户复制粘贴得到的也是错误的字符。
 *
 * <p>**只对已知兼容区做单字符归一化，不做整体 NFKC。**
 * 整体 NFKC 会把中文全角标点（，。）转成 ASCII 标点，破坏中文排版，
 * 也会改变全角字母数字，代价大于收益。
 */
public final class TextNormalizer {

    /** 康熙部首 U+2F00–U+2FDF */
    private static final char KANGXI_RADICAL_START = '⼀';
    private static final char KANGXI_RADICAL_END = '⿟';

    /** CJK 兼容表意文字 U+F900–U+FAFF */
    private static final char CJK_COMPAT_START = '豈';
    private static final char CJK_COMPAT_END = '﫿';

    /** CJK 部首补充 U+2E80–U+2EFF */
    private static final char CJK_RADICAL_SUPPLEMENT_START = '⺀';
    private static final char CJK_RADICAL_SUPPLEMENT_END = '⻿';

    private TextNormalizer() {
    }

    /**
     * 把兼容区的字符还原为标准汉字，其余字符原样保留。
     *
     * <p>逐字符判断再逐个归一化，因此不会波及其他字符。
     */
    public static String normalizeCompatibilityCharacters(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        boolean changed = false;
        StringBuilder result = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (isCompatibilityCharacter(c)) {
                String normalized = Normalizer.normalize(String.valueOf(c), Normalizer.Form.NFKC);
                // 有些码点在 NFKC 下没有对应汉字（返回自身），保持原样即可
                if (normalized.length() == 1 && normalized.charAt(0) != c) {
                    result.append(normalized);
                    changed = true;
                    continue;
                }
            }
            result.append(c);
        }
        return changed ? result.toString() : text;
    }

    private static boolean isCompatibilityCharacter(char c) {
        return (c >= KANGXI_RADICAL_START && c <= KANGXI_RADICAL_END)
                || (c >= CJK_COMPAT_START && c <= CJK_COMPAT_END)
                || (c >= CJK_RADICAL_SUPPLEMENT_START && c <= CJK_RADICAL_SUPPLEMENT_END);
    }
}
