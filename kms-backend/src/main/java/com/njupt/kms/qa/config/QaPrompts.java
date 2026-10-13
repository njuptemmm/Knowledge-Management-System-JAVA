package com.njupt.kms.qa.config;

import com.njupt.kms.qa.retrieve.RetrievedChunk;

import java.util.List;

/** RAG 的提示词。单独成类便于单测与评审。 */
public final class QaPrompts {

    /** 单块注入 prompt 的最大字符数，防止个别超长块吃光上下文预算。 */
    static final int MAX_CHUNK_CHARS = 1200;

    private QaPrompts() {
    }

    /**
     * 系统提示。四条规则缺一不可，其中第 2 条是防幻觉的核心：
     * 不强制「答不上来就说没有」的话，模型会用通用知识编一个看起来合理的答案，
     * RAG 就退化成了普通聊天，检索增强的价值归零。
     */
    public static final String SYSTEM = """
            你是知识库问答助手。请严格依据用户消息中提供的资料回答问题。

            规则：
            1. 只使用资料中的信息，不要引入资料之外的任何知识
            2. 资料不足以回答时，明确回答「知识库中没有相关内容」，不要编造
            3. 回答中用 [1][2] 标注依据来自哪条资料
            4. 若多条资料互相矛盾，指出矛盾并分别说明

            回答使用简体中文，简洁准确，不要复述资料原文。
            """;

    /** 把召回的块编号后拼进用户消息，编号供模型在回答里引用。 */
    public static String buildUserPrompt(String question, List<RetrievedChunk> chunks) {
        StringBuilder builder = new StringBuilder();

        if (chunks == null || chunks.isEmpty()) {
            // 在「没有资料」的位置就地重申规则，比只靠系统提示更不容易被模型忽略
            builder.append("""
                    【资料】
                    没有检索到相关资料。请直接回答「知识库中没有相关内容」，不要凭常识作答。

                    """);
        } else {
            builder.append("【资料】\n");
            for (int i = 0; i < chunks.size(); i++) {
                RetrievedChunk chunk = chunks.get(i);
                builder.append('[').append(i + 1).append("] 《")
                        .append(chunk.title()).append('》');
                if (chunk.headingPath() != null && !chunk.headingPath().isBlank()) {
                    builder.append(" / ").append(chunk.headingPath());
                }
                builder.append('\n').append(truncate(chunk.text())).append("\n\n");
            }
        }

        builder.append("【问题】\n").append(question);
        return builder.toString();
    }

    private static String truncate(String text) {
        if (text == null) {
            return "";
        }
        String stripped = text.strip();
        return stripped.length() <= MAX_CHUNK_CHARS
                ? stripped
                : stripped.substring(0, MAX_CHUNK_CHARS) + "……";
    }
}
