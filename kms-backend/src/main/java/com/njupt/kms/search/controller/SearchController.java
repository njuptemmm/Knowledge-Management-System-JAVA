package com.njupt.kms.search.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njupt.kms.common.Result;
import com.njupt.kms.search.dto.SearchHit;
import com.njupt.kms.search.dto.SearchQuery;
import com.njupt.kms.search.service.SearchService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/search")
@RequiredArgsConstructor
public class SearchController {

    private final SearchService searchService;

    /** 公开接口：未登录只搜到已发布条目。 */
    @GetMapping
    public Result<Page<SearchHit>> search(SearchQuery query) {
        return Result.ok(searchService.search(query));
    }
}
