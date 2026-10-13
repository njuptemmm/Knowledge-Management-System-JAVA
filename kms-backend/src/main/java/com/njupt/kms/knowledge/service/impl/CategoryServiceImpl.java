package com.njupt.kms.knowledge.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.njupt.kms.common.BusinessException;
import com.njupt.kms.knowledge.dto.CategoryNode;
import com.njupt.kms.knowledge.dto.CategorySaveRequest;
import com.njupt.kms.knowledge.entity.Category;
import com.njupt.kms.knowledge.entity.KnowledgeItem;
import com.njupt.kms.knowledge.mapper.CategoryMapper;
import com.njupt.kms.knowledge.mapper.KnowledgeItemMapper;
import com.njupt.kms.knowledge.service.CategoryService;
import com.njupt.kms.knowledge.service.CategoryTreeBuilder;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class CategoryServiceImpl implements CategoryService {

    /** 根分类的 parentId 约定值 */
    private static final long ROOT_PARENT_ID = 0L;

    private final CategoryMapper categoryMapper;
    private final KnowledgeItemMapper knowledgeItemMapper;

    @Override
    public List<CategoryNode> tree() {
        return CategoryTreeBuilder.build(all());
    }

    @Override
    public List<Category> all() {
        return categoryMapper.selectList(
                Wrappers.<Category>lambdaQuery()
                        .orderByAsc(Category::getSort)
                        .orderByAsc(Category::getId));
    }

    @Override
    @Transactional
    public Category create(CategorySaveRequest request) {
        long parentId = request.getParentId() == null ? ROOT_PARENT_ID : request.getParentId();
        int level = resolveLevel(parentId);

        assertNameUniqueUnderParent(request.getName(), parentId, null);

        Category category = new Category();
        category.setName(request.getName().trim());
        category.setParentId(parentId);
        category.setLevel(level);
        category.setSort(request.getSort() == null ? 0 : request.getSort());
        category.setDescription(request.getDescription());
        categoryMapper.insert(category);
        return category;
    }

    @Override
    @Transactional
    public Category update(Long id, CategorySaveRequest request) {
        Category existing = categoryMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("分类不存在");
        }

        // v0.0.1 不支持移动分类：改父节点需重算整棵子树的 level，
        // 收益低而回归风险高。请求中的 parentId 被忽略。
        assertNameUniqueUnderParent(request.getName(), existing.getParentId(), id);

        existing.setName(request.getName().trim());
        existing.setSort(request.getSort() == null ? 0 : request.getSort());
        existing.setDescription(request.getDescription());
        categoryMapper.updateById(existing);
        return existing;
    }

    @Override
    @Transactional
    public void delete(Long id) {
        Category existing = categoryMapper.selectById(id);
        if (existing == null) {
            throw new BusinessException("分类不存在");
        }

        Long childCount = categoryMapper.selectCount(
                Wrappers.<Category>lambdaQuery().eq(Category::getParentId, id));
        if (childCount != null && childCount > 0) {
            throw new BusinessException("该分类下有 " + childCount + " 个子分类，请先处理子分类");
        }

        Long itemCount = knowledgeItemMapper.selectCount(
                Wrappers.<KnowledgeItem>lambdaQuery().eq(KnowledgeItem::getCategoryId, id));
        if (itemCount != null && itemCount > 0) {
            throw new BusinessException("该分类下有 " + itemCount + " 个知识条目，无法删除");
        }

        categoryMapper.deleteById(id);
    }

    @Override
    public Set<Long> descendantIds(Long categoryId) {
        if (categoryId == null) {
            return Set.of();
        }
        return CategoryTreeBuilder.collectDescendantIds(all(), categoryId);
    }

    /** parentId → level。父分类必须存在。 */
    private int resolveLevel(long parentId) {
        if (parentId == ROOT_PARENT_ID) {
            return 1;
        }
        Category parent = categoryMapper.selectById(parentId);
        if (parent == null) {
            throw new BusinessException("父分类不存在");
        }
        return parent.getLevel() + 1;
    }

    /** 同一父节点下不允许重名；excludeId 用于更新时排除自身。 */
    private void assertNameUniqueUnderParent(String name, long parentId, Long excludeId) {
        Long count = categoryMapper.selectCount(
                Wrappers.<Category>lambdaQuery()
                        .eq(Category::getName, name.trim())
                        .eq(Category::getParentId, parentId)
                        .ne(excludeId != null, Category::getId, excludeId));
        if (count != null && count > 0) {
            throw new BusinessException("同级下已存在同名分类");
        }
    }
}
