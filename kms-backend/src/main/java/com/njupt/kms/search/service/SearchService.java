package com.njupt.kms.search.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njupt.kms.search.dto.SearchHit;
import com.njupt.kms.search.dto.SearchQuery;

public interface SearchService {

    Page<SearchHit> search(SearchQuery query);
}
