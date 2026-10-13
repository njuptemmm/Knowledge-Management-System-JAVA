package com.njupt.kms.knowledge.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.njupt.kms.knowledge.entity.ItemTag;
import com.njupt.kms.knowledge.mapper.ItemTagMapper;
import com.njupt.kms.knowledge.service.TagService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

@Service
@RequiredArgsConstructor
public class TagServiceImpl implements TagService {

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
}
