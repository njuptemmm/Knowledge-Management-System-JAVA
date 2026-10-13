package com.njupt.kms.search.service.impl;

import com.njupt.kms.search.dto.SearchQuery;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SearchQueryBuilderTest {

    @Test
    void keyword_is_trimmed_and_null_normalized() {
        SearchQuery query = new SearchQuery();
        query.setKeyword("  Java  ");
        assertThat(SearchServiceImpl.normalizeKeyword(query)).isEqualTo("Java");

        query.setKeyword("   ");
        assertThat(SearchServiceImpl.normalizeKeyword(query)).isNull();

        query.setKeyword(null);
        assertThat(SearchServiceImpl.normalizeKeyword(query)).isNull();
    }

    @Test
    void default_sort_is_relevance() {
        assertThat(SearchServiceImpl.resolveSortOrder(new SearchQuery())).isEqualTo("relevance");
    }

    @Test
    void unknown_sort_falls_back_to_relevance() {
        SearchQuery query = new SearchQuery();
        query.setSort("garbage");
        assertThat(SearchServiceImpl.resolveSortOrder(query)).isEqualTo("relevance");
    }

    @Test
    void known_sorts_are_preserved() {
        SearchQuery query = new SearchQuery();
        query.setSort("latest");
        assertThat(SearchServiceImpl.resolveSortOrder(query)).isEqualTo("latest");
        query.setSort("hottest");
        assertThat(SearchServiceImpl.resolveSortOrder(query)).isEqualTo("hottest");
    }

    @Test
    void page_and_size_are_clamped() {
        SearchQuery query = new SearchQuery();
        query.setPage(0);
        query.setSize(1000);
        assertThat(SearchServiceImpl.clampPage(query)).isEqualTo(1);
        // 上限 100，防止一次拉取过多数据
        assertThat(SearchServiceImpl.clampSize(query)).isEqualTo(100);
    }

    @Test
    void keyword_with_han_characters_uses_fulltext_path() {
        assertThat(SearchServiceImpl.prefersFullText("索引")).isTrue();
        assertThat(SearchServiceImpl.prefersFullText("数据库")).isTrue();
        assertThat(SearchServiceImpl.prefersFullText("MySQL 索引")).isTrue();
    }

    @Test
    void ascii_only_keyword_uses_like_path_because_ngram_skips_latin() {
        // 实测：ngram 解析器不索引拉丁字符，MATCH('Java') 恒为 0 分。
        // 纯英文/数字关键词必须降级到 LIKE 路径，否则搜索结果为空。
        assertThat(SearchServiceImpl.prefersFullText("Java")).isFalse();
        assertThat(SearchServiceImpl.prefersFullText("MySQL")).isFalse();
        assertThat(SearchServiceImpl.prefersFullText("JVM")).isFalse();
        assertThat(SearchServiceImpl.prefersFullText("Spring 6")).isFalse();
    }

    @Test
    void blank_keyword_uses_neither_path() {
        assertThat(SearchServiceImpl.prefersFullText(null)).isFalse();
        assertThat(SearchServiceImpl.prefersFullText("   ")).isFalse();
    }
}
