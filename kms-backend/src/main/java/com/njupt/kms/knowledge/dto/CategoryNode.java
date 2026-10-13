package com.njupt.kms.knowledge.dto;

import com.njupt.kms.knowledge.entity.Category;
import lombok.Data;

import java.util.ArrayList;
import java.util.List;

@Data
public class CategoryNode {

    private Long id;
    private String name;
    private Long parentId;
    private Integer level;
    private Integer sort;
    private String description;
    private List<CategoryNode> children = new ArrayList<>();

    public static CategoryNode from(Category category) {
        CategoryNode node = new CategoryNode();
        node.setId(category.getId());
        node.setName(category.getName());
        node.setParentId(category.getParentId());
        node.setLevel(category.getLevel());
        node.setSort(category.getSort());
        node.setDescription(category.getDescription());
        return node;
    }
}
