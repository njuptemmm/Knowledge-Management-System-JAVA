package com.njupt.kms.knowledge.entity;

import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

@Data
@TableName("item_tag")
public class ItemTag {

    private Long itemId;

    private Long tagId;
}
