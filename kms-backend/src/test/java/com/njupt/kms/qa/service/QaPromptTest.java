package com.njupt.kms.qa.service;

import com.njupt.kms.qa.config.QaPrompts;
import com.njupt.kms.qa.retrieve.RetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class QaPromptTest {

    private RetrievedChunk chunk(long id, String title, String path, String text) {
        return new RetrievedChunk(id, title, path, text, 0.9);
    }

    @Test
    void system_prompt_forbids_using_outside_knowledge() {
        // 不约束的话模型会用通用知识回答，答案看起来对但与知识库无关，
        // RAG 就退化成普通聊天，检索增强的价值归零
        assertThat(QaPrompts.SYSTEM).contains("只使用");
        assertThat(QaPrompts.SYSTEM).contains("知识库中没有相关内容");
    }

    @Test
    void system_prompt_requires_citation_markers() {
        assertThat(QaPrompts.SYSTEM).contains("[1][2]");
    }

    @Test
    void user_prompt_numbers_each_chunk_for_citation() {
        String prompt = QaPrompts.buildUserPrompt("Spring 的 Bean 何时实例化？", List.of(
                chunk(3, "Spring IoC 容器原理", "Spring > IoC", "BeanDefinition 加载、实例化……"),
                chunk(19, "软件架构风格对比", "架构风格", "分层与事件驱动的代价……")));

        assertThat(prompt).contains("[1]").contains("[2]");
        assertThat(prompt).contains("Spring IoC 容器原理");
        assertThat(prompt).contains("Spring > IoC");
        assertThat(prompt).contains("Spring 的 Bean 何时实例化？");
    }

    @Test
    void user_prompt_with_no_chunks_instructs_to_say_unknown() {
        String prompt = QaPrompts.buildUserPrompt("知识库里没有的问题", List.of());

        assertThat(prompt).contains("没有检索到");
        assertThat(prompt).contains("知识库中没有相关内容");
    }

    @Test
    void null_chunk_list_is_treated_as_no_results() {
        String prompt = QaPrompts.buildUserPrompt("问题", null);

        assertThat(prompt).contains("没有检索到");
        assertThat(prompt).contains("问题");
    }

    @Test
    void huge_chunk_is_truncated_to_protect_the_context_budget() {
        String huge = "很长的内容".repeat(2000);

        String prompt = QaPrompts.buildUserPrompt("问题", List.of(chunk(1, "T", "P", huge)));

        assertThat(prompt.length()).isLessThan(huge.length());
        assertThat(prompt).contains("……");
    }

    @Test
    void chunk_without_heading_path_renders_without_dangling_separator() {
        String prompt = QaPrompts.buildUserPrompt("问题", List.of(chunk(1, "标题", "", "正文")));

        assertThat(prompt).contains("《标题》");
        assertThat(prompt).doesNotContain("《标题》 / ");
    }

    @Test
    void chunk_with_null_heading_path_does_not_break() {
        String prompt = QaPrompts.buildUserPrompt("问题", List.of(chunk(1, "标题", null, "正文")));

        assertThat(prompt).contains("《标题》");
    }
}
