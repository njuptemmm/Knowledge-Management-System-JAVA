package com.njupt.kms.knowledge.service;

import com.njupt.kms.knowledge.dto.CategoryNode;
import com.njupt.kms.knowledge.entity.Category;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 分类树构建：邻接表（parent_id）→ 树形结构。
 *
 * <p>算法为「一次全表查询 + 内存组装」，时间复杂度 O(n log n)（排序主导），
 * 优于按层递归查库的 O(n × depth) 次数据库往返。
 *
 * <p>纯函数，不依赖 Spring 容器，便于独立测试。
 */
public final class CategoryTreeBuilder {

    private static final Comparator<CategoryNode> BY_SORT_THEN_ID =
            Comparator.comparing(CategoryNode::getSort, Comparator.nullsLast(Comparator.naturalOrder()))
                    .thenComparing(CategoryNode::getId);

    private CategoryTreeBuilder() {
    }

    /**
     * 把扁平的分类列表组装成树。
     *
     * <p>parentId 指向不存在的分类时，该节点降级为根节点（而非丢弃），
     * 避免数据异常导致分类静默消失、用户无从发现。
     */
    public static List<CategoryNode> build(List<Category> flat) {
        if (flat == null || flat.isEmpty()) {
            return new ArrayList<>();
        }

        // 第一遍：建 id → node 映射
        Map<Long, CategoryNode> nodeMap = new LinkedHashMap<>();
        for (Category category : flat) {
            nodeMap.put(category.getId(), CategoryNode.from(category));
        }

        // 第二遍：挂接父子关系
        List<CategoryNode> roots = new ArrayList<>();
        for (CategoryNode node : nodeMap.values()) {
            Long parentId = node.getParentId();
            CategoryNode parent = (parentId == null || parentId == 0L) ? null : nodeMap.get(parentId);
            if (parent == null) {
                roots.add(node);
            } else {
                parent.getChildren().add(node);
            }
        }

        sortRecursively(roots);
        return roots;
    }

    /**
     * 求某分类的全部后代 id（含自身）。用于「点击父分类时显示下级全部条目」。
     *
     * <p>使用显式栈而非递归，避免分类层级过深时栈溢出。
     */
    public static Set<Long> collectDescendantIds(List<Category> flat, Long rootId) {
        Set<Long> result = new HashSet<>();
        if (flat == null || flat.isEmpty() || rootId == null) {
            return result;
        }

        Map<Long, List<Long>> childrenOf = new HashMap<>();
        for (Category category : flat) {
            childrenOf.computeIfAbsent(category.getParentId(), k -> new ArrayList<>())
                    .add(category.getId());
        }

        Deque<Long> stack = new ArrayDeque<>();
        stack.push(rootId);
        while (!stack.isEmpty()) {
            Long current = stack.pop();
            if (!result.add(current)) {
                continue;   // 已访问，防止脏数据成环导致死循环
            }
            for (Long child : childrenOf.getOrDefault(current, List.of())) {
                stack.push(child);
            }
        }
        return result;
    }

    private static void sortRecursively(List<CategoryNode> nodes) {
        nodes.sort(BY_SORT_THEN_ID);
        for (CategoryNode node : nodes) {
            sortRecursively(node.getChildren());
        }
    }
}
