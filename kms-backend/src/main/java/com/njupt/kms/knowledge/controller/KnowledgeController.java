package com.njupt.kms.knowledge.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.njupt.kms.common.Result;
import com.njupt.kms.knowledge.dto.KnowledgeDetailVO;
import com.njupt.kms.knowledge.dto.KnowledgeListVO;
import com.njupt.kms.knowledge.dto.KnowledgeQuery;
import com.njupt.kms.knowledge.dto.KnowledgeSaveRequest;
import com.njupt.kms.knowledge.service.KnowledgeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/knowledge")
@RequiredArgsConstructor
public class KnowledgeController {

    private final KnowledgeService knowledgeService;

    /** 公开接口：未登录只能看到已发布条目。 */
    @GetMapping
    public Result<Page<KnowledgeListVO>> page(KnowledgeQuery query) {
        return Result.ok(knowledgeService.page(query));
    }

    @GetMapping("/{id}")
    public Result<KnowledgeDetailVO> detail(@PathVariable Long id) {
        return Result.ok(knowledgeService.detail(id));
    }

    @PostMapping
    public Result<KnowledgeDetailVO> create(@Valid @RequestBody KnowledgeSaveRequest request) {
        return Result.ok(knowledgeService.create(request));
    }

    @PutMapping("/{id}")
    public Result<KnowledgeDetailVO> update(@PathVariable Long id,
                                            @Valid @RequestBody KnowledgeSaveRequest request) {
        return Result.ok(knowledgeService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeService.delete(id);
        return Result.ok();
    }

    @PostMapping("/{id}/publish")
    public Result<KnowledgeDetailVO> publish(@PathVariable Long id) {
        return Result.ok(knowledgeService.publish(id));
    }

    @PostMapping("/{id}/archive")
    public Result<KnowledgeDetailVO> archive(@PathVariable Long id) {
        return Result.ok(knowledgeService.archive(id));
    }

    @PostMapping("/{id}/restore")
    public Result<KnowledgeDetailVO> restore(@PathVariable Long id) {
        return Result.ok(knowledgeService.restore(id));
    }
}
