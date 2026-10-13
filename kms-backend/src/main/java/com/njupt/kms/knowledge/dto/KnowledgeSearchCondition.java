package com.njupt.kms.knowledge.dto;

import lombok.Data;

import java.time.LocalDate;
import java.util.Collection;

/** M3 传给 M1 的检索条件。M1 据此拼 SQL，不感知 M3 的任何类型。 */
@Data
public class KnowledgeSearchCondition {

    private String keyword;

    /** 已展开为「本分类 + 全部后代」的 id 集合；为空表示不限分类 */
    private Collection<Long> categoryIds;

    private Long tagId;

    private String contentType;

    private String status;

    private LocalDate from;

    private LocalDate to;

    /** relevance / latest / hottest */
    private String sort;

    /** 非 null 时额外放行该作者的未发布条目 */
    private Long visibleAuthorId;

    private double weightTitle;
    private double weightSummary;
    private double weightContent;

    /**
     * true 走 ngram 全文索引（关键词含 CJK）；false 走 LIKE（纯 ASCII 关键词）。
     * 依据见设计文档 §5.2 第 3 点：ngram 解析器不索引拉丁字符。
     */
    private boolean useFullText;
}
