package com.njupt.kms.knowledge.service;

import com.njupt.kms.knowledge.dto.CategoryNode;
import com.njupt.kms.knowledge.dto.CategorySaveRequest;
import com.njupt.kms.knowledge.entity.Category;

import java.util.List;
import java.util.Set;

public interface CategoryService {

    /** 完整分类树。 */
    List<CategoryNode> tree();

    /** 全部分类的扁平列表，供树算法与后代计算使用。 */
    List<Category> all();

    Category create(CategorySaveRequest request);

    Category update(Long id, CategorySaveRequest request);

    void delete(Long id);

    /** 某分类的全部后代 id（含自身）。 */
    Set<Long> descendantIds(Long categoryId);

    /** 分类名。id 为空或分类不存在时返回 null。 */
    String nameOf(Long categoryId);
}
