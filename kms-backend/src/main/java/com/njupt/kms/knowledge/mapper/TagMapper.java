package com.njupt.kms.knowledge.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.njupt.kms.knowledge.dto.TagVO;
import com.njupt.kms.knowledge.entity.Tag;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TagMapper extends BaseMapper<Tag> {

    /** 标签及其被引用次数，按引用次数降序。 */
    @Select("""
            SELECT t.id, t.name, COUNT(it.item_id) AS itemCount
            FROM tag t
            LEFT JOIN item_tag it ON it.tag_id = t.id
            GROUP BY t.id, t.name
            ORDER BY itemCount DESC, t.id ASC
            """)
    List<TagVO> selectWithCount();
}
