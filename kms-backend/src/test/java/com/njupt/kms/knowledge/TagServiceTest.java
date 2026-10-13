package com.njupt.kms.knowledge;

import com.njupt.kms.knowledge.entity.ItemTag;
import com.njupt.kms.knowledge.entity.Tag;
import com.njupt.kms.knowledge.mapper.ItemTagMapper;
import com.njupt.kms.knowledge.mapper.TagMapper;
import com.njupt.kms.knowledge.service.TagService;
import com.njupt.kms.knowledge.service.impl.TagServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TagServiceTest {

    private TagMapper tagMapper;
    private ItemTagMapper itemTagMapper;
    private TagService tagService;

    @BeforeEach
    void setUp() {
        tagMapper = mock(TagMapper.class);
        itemTagMapper = mock(ItemTagMapper.class);
        tagService = new TagServiceImpl(tagMapper, itemTagMapper);
    }

    private Tag tag(Long id, String name) {
        Tag t = new Tag();
        t.setId(id);
        t.setName(name);
        return t;
    }

    @Test
    void find_or_create_reuses_existing_tag() {
        when(tagMapper.selectOne(any())).thenReturn(tag(3L, "面试"));

        Tag result = tagService.findOrCreate("面试");

        assertThat(result.getId()).isEqualTo(3L);
        verify(tagMapper, never()).insert(any(Tag.class));
    }

    @Test
    void find_or_create_inserts_new_tag() {
        when(tagMapper.selectOne(any())).thenReturn(null);
        when(tagMapper.insert(any(Tag.class))).thenAnswer(inv -> {
            ((Tag) inv.getArgument(0)).setId(9L);
            return 1;
        });

        Tag result = tagService.findOrCreate("新标签");

        assertThat(result.getId()).isEqualTo(9L);
        assertThat(result.getName()).isEqualTo("新标签");
    }

    @Test
    void find_or_create_trims_and_ignores_blank() {
        assertThat(tagService.findOrCreate("   ")).isNull();
        assertThat(tagService.findOrCreate(null)).isNull();
        verify(tagMapper, never()).insert(any(Tag.class));
    }

    @Test
    void find_or_create_truncates_overlong_name() {
        when(tagMapper.selectOne(any())).thenReturn(null);
        when(tagMapper.insert(any(Tag.class))).thenAnswer(inv -> {
            ((Tag) inv.getArgument(0)).setId(1L);
            return 1;
        });

        Tag result = tagService.findOrCreate("超".repeat(80));

        // tag.name 列宽 50，超出必须截断，否则插入报错
        assertThat(result.getName()).hasSize(50);
    }

    @Test
    void replace_tags_by_names_dedupes_repeats_and_skips_blanks() {
        when(tagMapper.selectOne(any())).thenReturn(tag(1L, "Java"));

        tagService.replaceTagsByNames(5L, List.of("Java", "Java", "  ", " Java "));

        verify(itemTagMapper).delete(any());
        // 四个输入规范化后只剩一个 "Java"
        verify(itemTagMapper, times(1)).insert(any(ItemTag.class));
    }

    @Test
    void replace_tags_by_names_inserts_one_link_per_distinct_name() {
        AtomicLong seq = new AtomicLong(1);
        when(tagMapper.selectOne(any())).thenReturn(null);
        when(tagMapper.insert(any(Tag.class))).thenAnswer(inv -> {
            ((Tag) inv.getArgument(0)).setId(seq.getAndIncrement());
            return 1;
        });

        tagService.replaceTagsByNames(5L, List.of("Java", "面试"));

        verify(itemTagMapper).delete(any());
        verify(itemTagMapper, times(2)).insert(any(ItemTag.class));
    }

    @Test
    void replace_tags_by_names_accepts_empty_list() {
        tagService.replaceTagsByNames(5L, List.of());

        verify(itemTagMapper).delete(any());
        verify(itemTagMapper, never()).insert(any(ItemTag.class));
    }

    @Test
    void replace_tags_by_ids_skips_null_and_duplicate_ids() {
        tagService.replaceTags(5L, java.util.Arrays.asList(1L, 1L, null, 2L));

        verify(itemTagMapper, times(2)).insert(any(ItemTag.class));
    }

    @Test
    void list_with_count_delegates_to_mapper() {
        when(tagMapper.selectWithCount()).thenReturn(List.of());

        assertThat(tagService.listWithCount()).isEmpty();

        verify(tagMapper).selectWithCount();
    }
}
