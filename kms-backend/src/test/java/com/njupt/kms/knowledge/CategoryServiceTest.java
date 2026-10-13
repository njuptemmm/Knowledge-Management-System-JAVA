package com.njupt.kms.knowledge;

import com.njupt.kms.common.BusinessException;
import com.njupt.kms.knowledge.dto.CategoryNode;
import com.njupt.kms.knowledge.dto.CategorySaveRequest;
import com.njupt.kms.knowledge.entity.Category;
import com.njupt.kms.knowledge.entity.KnowledgeItem;
import com.njupt.kms.knowledge.mapper.CategoryMapper;
import com.njupt.kms.knowledge.mapper.KnowledgeItemMapper;
import com.njupt.kms.knowledge.service.CategoryService;
import com.njupt.kms.knowledge.service.impl.CategoryServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CategoryServiceTest {

    private CategoryMapper categoryMapper;
    private KnowledgeItemMapper knowledgeItemMapper;
    private CategoryService categoryService;

    @BeforeEach
    void setUp() {
        categoryMapper = mock(CategoryMapper.class);
        knowledgeItemMapper = mock(KnowledgeItemMapper.class);
        categoryService = new CategoryServiceImpl(categoryMapper, knowledgeItemMapper);
    }

    private Category cat(Long id, Long parentId, int level, String name) {
        Category c = new Category();
        c.setId(id);
        c.setParentId(parentId);
        c.setLevel(level);
        c.setName(name);
        c.setSort(0);
        return c;
    }

    private CategorySaveRequest req(String name, Long parentId) {
        CategorySaveRequest r = new CategorySaveRequest();
        r.setName(name);
        r.setParentId(parentId);
        r.setSort(0);
        return r;
    }

    @Test
    void create_at_root_sets_level_one() {
        when(categoryMapper.selectCount(any())).thenReturn(0L);

        Category created = categoryService.create(req("编程语言", 0L));

        assertThat(created.getLevel()).isEqualTo(1);
    }

    @Test
    void create_under_parent_sets_level_parent_plus_one() {
        when(categoryMapper.selectById(2L)).thenReturn(cat(2L, 0L, 1, "编程语言"));
        when(categoryMapper.selectCount(any())).thenReturn(0L);

        Category created = categoryService.create(req("Java", 2L));

        assertThat(created.getLevel()).isEqualTo(2);
        assertThat(created.getParentId()).isEqualTo(2L);
    }

    @Test
    void create_rejects_missing_parent() {
        when(categoryMapper.selectById(999L)).thenReturn(null);

        assertThatThrownBy(() -> categoryService.create(req("Java", 999L)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("父分类不存在");
    }

    @Test
    void create_rejects_duplicate_name_under_same_parent() {
        when(categoryMapper.selectById(2L)).thenReturn(cat(2L, 0L, 1, "编程语言"));
        when(categoryMapper.selectCount(any())).thenReturn(1L);

        assertThatThrownBy(() -> categoryService.create(req("Java", 2L)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("同级下已存在同名分类");
    }

    @Test
    void create_trims_whitespace_from_name() {
        when(categoryMapper.selectCount(any())).thenReturn(0L);

        Category created = categoryService.create(req("  编程语言  ", 0L));

        assertThat(created.getName()).isEqualTo("编程语言");
    }

    @Test
    void update_rejects_missing_category() {
        when(categoryMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> categoryService.update(99L, req("新名字", 0L)))
                .isInstanceOf(BusinessException.class)
                .hasMessage("分类不存在");
    }

    @Test
    void update_ignores_parent_change() {
        // v0.0.1 不支持移动分类：改父节点需重算整棵子树的 level，
        // 收益低而回归风险高。parentId 变更被忽略。
        Category existing = cat(3L, 2L, 2, "Java");
        when(categoryMapper.selectById(3L)).thenReturn(existing);
        when(categoryMapper.selectCount(any())).thenReturn(0L);

        Category updated = categoryService.update(3L, req("Java 语言", 0L));

        assertThat(updated.getLevel()).isEqualTo(2);
        assertThat(updated.getParentId()).isEqualTo(2L);
        assertThat(updated.getName()).isEqualTo("Java 语言");
    }

    @Test
    void delete_rejects_category_with_children() {
        when(categoryMapper.selectById(2L)).thenReturn(cat(2L, 0L, 1, "编程语言"));
        when(categoryMapper.selectCount(any())).thenReturn(2L);

        assertThatThrownBy(() -> categoryService.delete(2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("子分类");
    }

    @Test
    void delete_rejects_category_referenced_by_items() {
        when(categoryMapper.selectById(2L)).thenReturn(cat(2L, 0L, 1, "编程语言"));
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(knowledgeItemMapper.selectCount(any())).thenReturn(5L);

        assertThatThrownBy(() -> categoryService.delete(2L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("5 个知识条目");
    }

    @Test
    void delete_removes_empty_leaf_category() {
        when(categoryMapper.selectById(3L)).thenReturn(cat(3L, 2L, 2, "Java"));
        when(categoryMapper.selectCount(any())).thenReturn(0L);
        when(knowledgeItemMapper.selectCount(any())).thenReturn(0L);

        categoryService.delete(3L);

        org.mockito.Mockito.verify(categoryMapper).deleteById(3L);
    }

    @Test
    void descendant_ids_include_self_and_children() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(
                cat(1L, 0L, 1, "编程语言"),
                cat(2L, 1L, 2, "Java"),
                cat(3L, 2L, 3, "Spring")
        ));

        assertThat(categoryService.descendantIds(1L)).containsExactlyInAnyOrder(1L, 2L, 3L);
    }

    @Test
    void descendant_ids_of_null_returns_empty_set() {
        assertThat(categoryService.descendantIds(null)).isEmpty();
    }

    @Test
    void tree_returns_nested_structure() {
        when(categoryMapper.selectList(any())).thenReturn(List.of(
                cat(1L, 0L, 1, "编程语言"),
                cat(2L, 1L, 2, "Java")
        ));

        List<CategoryNode> tree = categoryService.tree();

        assertThat(tree).hasSize(1);
        assertThat(tree.get(0).getChildren()).hasSize(1);
    }
}
