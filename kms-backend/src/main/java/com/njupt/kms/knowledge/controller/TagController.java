package com.njupt.kms.knowledge.controller;

import com.njupt.kms.common.Result;
import com.njupt.kms.knowledge.dto.TagVO;
import com.njupt.kms.knowledge.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/tags")
@RequiredArgsConstructor
public class TagController {

    private final TagService tagService;

    /** 公开接口：浏览与筛选不需要登录。 */
    @GetMapping
    public Result<List<TagVO>> list() {
        return Result.ok(tagService.listWithCount());
    }
}
