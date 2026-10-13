package com.njupt.kms.knowledge;

import com.njupt.kms.knowledge.dto.CategoryNode;
import com.njupt.kms.knowledge.entity.Category;
import com.njupt.kms.knowledge.service.CategoryTreeBuilder;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class CategoryTreeBuilderTest {

    private Category cat(Long id, Long parentId, String name, int sort) {
        Category c = new Category();
        c.setId(id);
        c.setParentId(parentId);
        c.setName(name);
        c.setSort(sort);
        return c;
    }

    @Test
    void empty_input_yields_empty_tree() {
        assertThat(CategoryTreeBuilder.build(List.of())).isEmpty();
    }

    @Test
    void builds_three_level_tree() {
        List<Category> flat = List.of(
                cat(1L, 0L, "编程语言", 1),
                cat(2L, 1L, "Java", 1),
                cat(3L, 1L, "Python", 2),
                cat(4L, 2L, "Spring", 1),
                cat(5L, 2L, "JVM", 2)
        );

        List<CategoryNode> tree = CategoryTreeBuilder.build(flat);

        assertThat(tree).hasSize(1);
        CategoryNode root = tree.get(0);
        assertThat(root.getName()).isEqualTo("编程语言");
        assertThat(root.getChildren()).extracting(CategoryNode::getName)
                .containsExactly("Java", "Python");
        assertThat(root.getChildren().get(0).getChildren())
                .extracting(CategoryNode::getName)
                .containsExactly("Spring", "JVM");
        assertThat(root.getChildren().get(1).getChildren()).isEmpty();
    }

    @Test
    void sorts_siblings_by_sort_field_regardless_of_input_order() {
        List<Category> flat = List.of(
                cat(3L, 1L, "Python", 2),
                cat(1L, 0L, "编程语言", 1),
                cat(2L, 1L, "Java", 1)
        );

        List<CategoryNode> tree = CategoryTreeBuilder.build(flat);

        assertThat(tree.get(0).getChildren()).extracting(CategoryNode::getName)
                .containsExactly("Java", "Python");
    }

    @Test
    void supports_multiple_roots() {
        List<Category> flat = List.of(
                cat(10L, 0L, "数据库", 2),
                cat(1L, 0L, "编程语言", 1),
                cat(20L, 0L, "软件工程", 3)
        );

        assertThat(CategoryTreeBuilder.build(flat)).extracting(CategoryNode::getName)
                .containsExactly("编程语言", "数据库", "软件工程");
    }

    @Test
    void orphan_node_falls_back_to_root_instead_of_being_lost() {
        // parentId=999 不存在。选择降级为根节点而非丢弃，避免数据静默丢失。
        List<Category> flat = List.of(
                cat(1L, 0L, "编程语言", 1),
                cat(9L, 999L, "孤儿分类", 1)
        );

        assertThat(CategoryTreeBuilder.build(flat)).extracting(CategoryNode::getName)
                .containsExactly("编程语言", "孤儿分类");
    }

    @Test
    void collect_descendant_ids_includes_self_and_all_levels() {
        List<Category> flat = List.of(
                cat(1L, 0L, "编程语言", 1),
                cat(2L, 1L, "Java", 1),
                cat(3L, 1L, "Python", 2),
                cat(4L, 2L, "Spring", 1)
        );

        assertThat(CategoryTreeBuilder.collectDescendantIds(flat, 1L))
                .containsExactlyInAnyOrder(1L, 2L, 3L, 4L);
        assertThat(CategoryTreeBuilder.collectDescendantIds(flat, 2L))
                .containsExactlyInAnyOrder(2L, 4L);
        assertThat(CategoryTreeBuilder.collectDescendantIds(flat, 3L))
                .containsExactly(3L);
    }

    @Test
    void descendant_lookup_survives_cyclic_data() {
        // 脏数据成环（2 的父是 3，3 的父是 2）：必须终止而不是死循环
        List<Category> flat = List.of(
                cat(1L, 0L, "根", 1),
                cat(2L, 3L, "A", 1),
                cat(3L, 2L, "B", 1)
        );

        Set<Long> ids = CategoryTreeBuilder.collectDescendantIds(flat, 2L);

        assertThat(ids).containsExactlyInAnyOrder(2L, 3L);
    }
}
