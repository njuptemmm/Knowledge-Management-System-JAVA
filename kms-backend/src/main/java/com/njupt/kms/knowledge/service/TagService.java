package com.njupt.kms.knowledge.service;

import com.njupt.kms.knowledge.dto.TagVO;
import com.njupt.kms.knowledge.entity.Tag;

import java.util.List;
import java.util.Optional;

public interface TagService {

    /** 覆盖式替换某条目的标签（按 id）。传空列表表示清空。 */
    void replaceTags(Long itemId, List<Long> tagIds);

    /** 覆盖式替换某条目的标签（按名称，自动创建不存在的标签）。 */
    void replaceTagsByNames(Long itemId, List<String> names);

    /** 按名称查找，不存在则创建。名称为空白时返回 null。 */
    Tag findOrCreate(String name);

    Optional<Tag> findByName(String name);

    /** 标签列表，含引用次数。 */
    List<TagVO> listWithCount();

    /** 某条目的标签名，按名称排序。 */
    List<String> namesOf(Long itemId);
}
