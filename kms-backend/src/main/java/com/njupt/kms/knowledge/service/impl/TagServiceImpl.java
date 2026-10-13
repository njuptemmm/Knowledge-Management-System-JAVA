package com.njupt.kms.knowledge.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.njupt.kms.knowledge.dto.TagVO;
import com.njupt.kms.knowledge.entity.ItemTag;
import com.njupt.kms.knowledge.entity.Tag;
import com.njupt.kms.knowledge.mapper.ItemTagMapper;
import com.njupt.kms.knowledge.mapper.TagMapper;
import com.njupt.kms.knowledge.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

    /** tag.name 列宽 50，超出直接截断，避免插入报错 */
    private static final int MAX_NAME_LENGTH = 50;

    private final TagMapper tagMapper;
    private final ItemTagMapper itemTagMapper;

    @Override
    @Transactional
    public void replaceTags(Long itemId, List<Long> tagIds) {
        itemTagMapper.delete(Wrappers.<ItemTag>lambdaQuery().eq(ItemTag::getItemId, itemId));
        if (tagIds == null || tagIds.isEmpty()) {
            return;
        }
        for (Long tagId : tagIds.stream().filter(Objects::nonNull).distinct().toList()) {
            ItemTag link = new ItemTag();
            link.setItemId(itemId);
            link.setTagId(tagId);
            itemTagMapper.insert(link);
        }
    }

    @Override
    @Transactional
    public void replaceTagsByNames(Long itemId, List<String> names) {
        itemTagMapper.delete(Wrappers.<ItemTag>lambdaQuery().eq(ItemTag::getItemId, itemId));
        if (names == null || names.isEmpty()) {
            return;
        }
        // 先规范化再 distinct，保证 "Java" 与 " java " 不会产生两条关联
        List<Tag> tags = names.stream()
                .filter(StringUtils::hasText)
                .map(this::normalize)
                .distinct()
                .map(this::findOrCreate)
                .filter(Objects::nonNull)
                .toList();

        for (Tag tag : tags) {
            ItemTag link = new ItemTag();
            link.setItemId(itemId);
            link.setTagId(tag.getId());
            itemTagMapper.insert(link);
        }
    }

    @Override
    @Transactional
    public Tag findOrCreate(String name) {
        if (!StringUtils.hasText(name)) {
            return null;
        }
        String normalized = normalize(name);

        Optional<Tag> existing = findByName(normalized);
        if (existing.isPresent()) {
            return existing.get();
        }

        Tag tag = new Tag();
        tag.setName(normalized);
        try {
            tagMapper.insert(tag);
            return tag;
        } catch (DuplicateKeyException e) {
            // 并发下两个请求同时创建同一标签：重查一次拿到已存在的那条
            return findByName(normalized).orElse(null);
        }
    }

    @Override
    public Optional<Tag> findByName(String name) {
        if (!StringUtils.hasText(name)) {
            return Optional.empty();
        }
        return Optional.ofNullable(
                tagMapper.selectOne(Wrappers.<Tag>lambdaQuery().eq(Tag::getName, normalize(name))));
    }

    @Override
    public List<TagVO> listWithCount() {
        return tagMapper.selectWithCount();
    }

    private String normalize(String name) {
        String trimmed = name.trim();
        return trimmed.length() > MAX_NAME_LENGTH ? trimmed.substring(0, MAX_NAME_LENGTH) : trimmed;
    }
}
