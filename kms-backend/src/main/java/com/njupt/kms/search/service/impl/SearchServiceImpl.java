package com.njupt.kms.search.service.impl;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njupt.kms.config.SearchProperties;
import com.njupt.kms.knowledge.dto.KnowledgeSearchCondition;
import com.njupt.kms.knowledge.dto.KnowledgeSearchRow;
import com.njupt.kms.knowledge.service.CategoryService;
import com.njupt.kms.knowledge.service.KnowledgeSearchPort;
import com.njupt.kms.search.dto.SearchHit;
import com.njupt.kms.search.dto.SearchQuery;
import com.njupt.kms.search.service.SearchService;
import com.njupt.kms.security.CurrentUser;
import com.njupt.kms.security.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class SearchServiceImpl implements SearchService {

    private static final int MAX_PAGE_SIZE = 100;
    private static final Set<String> ALLOWED_SORTS = Set.of("relevance", "latest", "hottest");

    private final KnowledgeSearchPort knowledgeSearchPort;
    private final CategoryService categoryService;
    private final SearchProperties searchProperties;

    @Override
    public Page<SearchHit> search(SearchQuery query) {
        int page = clampPage(query);
        int size = clampSize(query);

        User current = CurrentUser.get();
        Long visibleAuthorId = Boolean.TRUE.equals(query.getMine()) && current != null
                ? current.getId()
                : null;

        String keyword = normalizeKeyword(query);

        KnowledgeSearchCondition condition = new KnowledgeSearchCondition();
        condition.setKeyword(keyword);
        // ngram 解析器不索引拉丁字符：含 CJK 走全文索引，纯 ASCII 降级 LIKE
        condition.setUseFullText(prefersFullText(keyword));
        // 点击父分类要能看到下级条目，因此展开为全部后代 id
        condition.setCategoryIds(query.getCategoryId() == null
                ? null
                : categoryService.descendantIds(query.getCategoryId()));
        condition.setTagId(query.getTagId());
        condition.setContentType(query.getContentType());
        condition.setStatus(query.getStatus());
        condition.setFrom(query.getFrom());
        condition.setTo(query.getTo());
        condition.setSort(resolveSortOrder(query));
        condition.setVisibleAuthorId(visibleAuthorId);
        condition.setWeightTitle(searchProperties.getWeight().getTitle());
        condition.setWeightSummary(searchProperties.getWeight().getSummary());
        condition.setWeightContent(searchProperties.getWeight().getContent());

        IPage<KnowledgeSearchRow> rows = knowledgeSearchPort.search(condition, page, size);

        // M1 的原始行 → M3 的对外 DTO。两个模块各自持有自己的类型，避免互相依赖。
        List<SearchHit> hits = rows.getRecords().stream().map(SearchServiceImpl::toHit).toList();
        attachTags(hits);

        Page<SearchHit> result = new Page<>(rows.getCurrent(), rows.getSize(), rows.getTotal());
        result.setRecords(hits);
        return result;
    }

    static SearchHit toHit(KnowledgeSearchRow row) {
        SearchHit hit = new SearchHit();
        hit.setId(row.getId());
        hit.setTitle(row.getTitle());
        hit.setSnippet(row.getSummary());
        hit.setCategoryId(row.getCategoryId());
        hit.setCategoryName(row.getCategoryName());
        hit.setStatus(row.getStatus());
        hit.setUpdatedAt(row.getUpdatedAt());
        hit.setScore(row.getScore());
        return hit;
    }

    /** 批量填充标签，一次查询完成，避免逐条查库。 */
    private void attachTags(List<SearchHit> records) {
        if (records == null || records.isEmpty()) {
            return;
        }
        List<Long> ids = records.stream().map(SearchHit::getId).toList();
        Map<Long, List<String>> tagMap = knowledgeSearchPort.tagNamesOf(ids);
        for (SearchHit hit : records) {
            hit.setTags(tagMap.getOrDefault(hit.getId(), new ArrayList<>()));
        }
    }

    static String normalizeKeyword(SearchQuery query) {
        return StringUtils.hasText(query.getKeyword()) ? query.getKeyword().trim() : null;
    }

    /**
     * 关键词是否应走 ngram 全文索引。
     *
     * <p>实测（MySQL 8.0.41，ngram_token_size=2）：ngram 解析器只对 CJK 字符生成 token，
     * 拉丁字母与数字完全不进索引，{@code MATCH(title) AGAINST('Java')} 恒为 0 分。
     * 因此含 CJK 才走全文索引，纯 ASCII 必须降级为 LIKE，否则英文技术名词搜不到。
     * 详见设计文档 §5.2 第 3 点。
     */
    static boolean prefersFullText(String keyword) {
        if (!StringUtils.hasText(keyword)) {
            return false;
        }
        for (int i = 0; i < keyword.length(); i++) {
            char c = keyword.charAt(i);
            // CJK 统一表意文字 + 扩展 A + 兼容表意文字，覆盖本项目可能出现的全部中文
            if ((c >= 0x4E00 && c <= 0x9FFF)
                    || (c >= 0x3400 && c <= 0x4DBF)
                    || (c >= 0xF900 && c <= 0xFAFF)) {
                return true;
            }
        }
        return false;
    }

    static String resolveSortOrder(SearchQuery query) {
        String sort = query.getSort();
        return (sort != null && ALLOWED_SORTS.contains(sort)) ? sort : "relevance";
    }

    static int clampPage(SearchQuery query) {
        Integer page = query.getPage();
        return (page == null || page < 1) ? 1 : page;
    }

    static int clampSize(SearchQuery query) {
        Integer size = query.getSize();
        if (size == null || size < 1) {
            return 10;
        }
        return Math.min(size, MAX_PAGE_SIZE);
    }
}
