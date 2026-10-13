package com.njupt.kms.knowledge;

import com.njupt.kms.common.BusinessException;
import com.njupt.kms.knowledge.dto.KnowledgeDetailVO;
import com.njupt.kms.knowledge.dto.KnowledgeSaveRequest;
import com.njupt.kms.knowledge.entity.KnowledgeItem;
import com.njupt.kms.knowledge.mapper.KnowledgeItemMapper;
import com.njupt.kms.knowledge.service.CategoryService;
import com.njupt.kms.knowledge.service.KnowledgeService;
import com.njupt.kms.knowledge.service.TagService;
import com.njupt.kms.knowledge.service.impl.KnowledgeServiceImpl;
import com.njupt.kms.security.CurrentUser;
import com.njupt.kms.security.User;
import com.njupt.kms.security.UserQueryPort;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeServiceTest {

    private KnowledgeItemMapper itemMapper;
    private TagService tagService;
    private KnowledgeService knowledgeService;

    @BeforeEach
    void setUp() {
        itemMapper = mock(KnowledgeItemMapper.class);
        tagService = mock(TagService.class);
        knowledgeService = new KnowledgeServiceImpl(itemMapper, tagService, mock(CategoryService.class), mock(UserQueryPort.class));
    }

    @AfterEach
    void tearDown() {
        CurrentUser.clear();
    }

    private void loginAs(long userId, String role) {
        User user = new User();
        user.setId(userId);
        user.setUsername("u" + userId);
        user.setRole(role);
        user.setStatus(1);
        CurrentUser.set(user);
    }

    private KnowledgeItem item(Long id, Long authorId) {
        KnowledgeItem it = new KnowledgeItem();
        it.setId(id);
        it.setTitle("原标题");
        it.setContent("原内容");
        it.setStatus("DRAFT");
        it.setAuthorId(authorId);
        it.setViewCount(0);
        return it;
    }

    private KnowledgeSaveRequest req(String title) {
        KnowledgeSaveRequest r = new KnowledgeSaveRequest();
        r.setTitle(title);
        r.setContent("内容");
        r.setSummary("摘要");
        return r;
    }

    @Test
    void create_defaults_to_draft_and_current_author() {
        loginAs(2L, "USER");
        when(itemMapper.insert(any(KnowledgeItem.class))).thenAnswer(inv -> {
            ((KnowledgeItem) inv.getArgument(0)).setId(100L);
            return 1;
        });

        KnowledgeDetailVO vo = knowledgeService.create(req("新条目"));

        assertThat(vo.getStatus()).isEqualTo("DRAFT");
        assertThat(vo.getAuthorId()).isEqualTo(2L);
    }

    @Test
    void create_trims_title() {
        loginAs(2L, "USER");
        when(itemMapper.insert(any(KnowledgeItem.class))).thenAnswer(inv -> {
            ((KnowledgeItem) inv.getArgument(0)).setId(101L);
            return 1;
        });

        KnowledgeDetailVO vo = knowledgeService.create(req("  带空格的标题  "));

        assertThat(vo.getTitle()).isEqualTo("带空格的标题");
    }

    @Test
    void create_rejects_anonymous() {
        // 未设置 CurrentUser：getRequired() 必须拒绝
        assertThatThrownBy(() -> knowledgeService.create(req("匿名")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请先登录");
    }

    @Test
    void detail_increments_view_count() {
        loginAs(2L, "USER");
        when(itemMapper.selectById(1L)).thenReturn(item(1L, 2L));

        knowledgeService.detail(1L);

        verify(itemMapper).incrementViewCount(1L);
    }

    @Test
    void detail_rejects_missing_item() {
        loginAs(2L, "USER");
        when(itemMapper.selectById(99L)).thenReturn(null);

        assertThatThrownBy(() -> knowledgeService.detail(99L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("知识条目不存在");
    }

    @Test
    void update_allows_author() {
        loginAs(2L, "USER");
        when(itemMapper.selectById(1L)).thenReturn(item(1L, 2L));

        knowledgeService.update(1L, req("改后标题"));

        verify(itemMapper).updateById(any(KnowledgeItem.class));
    }

    @Test
    void update_rejects_non_author_non_admin() {
        loginAs(7L, "USER");
        when(itemMapper.selectById(1L)).thenReturn(item(1L, 2L));

        assertThatThrownBy(() -> knowledgeService.update(1L, req("越权改")))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权操作他人的知识条目");
        verify(itemMapper, never()).updateById(any(KnowledgeItem.class));
    }

    @Test
    void update_allows_admin_on_others_item() {
        loginAs(1L, "ADMIN");
        when(itemMapper.selectById(1L)).thenReturn(item(1L, 2L));

        knowledgeService.update(1L, req("管理员改"));

        verify(itemMapper).updateById(any(KnowledgeItem.class));
    }

    @Test
    void delete_rejects_non_author_non_admin() {
        loginAs(7L, "USER");
        when(itemMapper.selectById(1L)).thenReturn(item(1L, 2L));

        assertThatThrownBy(() -> knowledgeService.delete(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权操作他人的知识条目");
        verify(itemMapper, never()).deleteById(any(Long.class));
    }

    @Test
    void delete_clears_tag_links_first() {
        loginAs(2L, "USER");
        when(itemMapper.selectById(1L)).thenReturn(item(1L, 2L));

        knowledgeService.delete(1L);

        verify(tagService).replaceTags(1L, List.of());
        verify(itemMapper, times(1)).deleteById(1L);
    }

    @Test
    void get_entity_does_not_increment_view_count() {
        when(itemMapper.selectById(1L)).thenReturn(item(1L, 2L));

        knowledgeService.getEntity(1L);

        verify(itemMapper, never()).incrementViewCount(any(Long.class));
    }
}
