package com.njupt.kms.knowledge.controller;

import com.njupt.kms.common.Result;
import com.njupt.kms.knowledge.dto.CategoryNode;
import com.njupt.kms.knowledge.dto.CategorySaveRequest;
import com.njupt.kms.knowledge.entity.Category;
import com.njupt.kms.knowledge.service.CategoryService;
import com.njupt.kms.security.CurrentUser;
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

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    /** 公开接口：浏览不需要登录。 */
    @GetMapping("/tree")
    public Result<List<CategoryNode>> tree() {
        return Result.ok(categoryService.tree());
    }

    @PostMapping
    public Result<Category> create(@Valid @RequestBody CategorySaveRequest request) {
        CurrentUser.getRequired();
        return Result.ok(categoryService.create(request));
    }

    @PutMapping("/{id}")
    public Result<Category> update(@PathVariable Long id, @Valid @RequestBody CategorySaveRequest request) {
        CurrentUser.getRequired();
        return Result.ok(categoryService.update(id, request));
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        CurrentUser.getRequired();
        categoryService.delete(id);
        return Result.ok();
    }
}
