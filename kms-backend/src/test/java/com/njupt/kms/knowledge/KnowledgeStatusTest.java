package com.njupt.kms.knowledge;

import com.njupt.kms.common.BusinessException;
import com.njupt.kms.knowledge.dto.KnowledgeDetailVO;
import com.njupt.kms.knowledge.entity.KnowledgeItem;
import com.njupt.kms.knowledge.mapper.KnowledgeItemMapper;
import com.njupt.kms.knowledge.service.CategoryService;
import com.njupt.kms.knowledge.service.KnowledgeService;
import com.njupt.kms.knowledge.service.TagService;
import com.njupt.kms.knowledge.service.impl.KnowledgeServiceImpl;
import com.njupt.kms.security.CurrentUser;
import com.njupt.kms.security.User;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeStatusTest {

    private KnowledgeItemMapper itemMapper;
    private KnowledgeService knowledgeService;

    @BeforeEach
    void setUp() {
        itemMapper = mock(KnowledgeItemMapper.class);
        knowledgeService = new KnowledgeServiceImpl(
                itemMapper, mock(TagService.class), mock(CategoryService.class));
        loginAs(2L, "USER");
    }

    @AfterEach
    void tearDown() {
        CurrentUser.clear();
    }

    private void loginAs(long userId, String role) {
        User user = new User();
        user.setId(userId);
        user.setRole(role);
        user.setStatus(1);
        CurrentUser.set(user);
    }

    private KnowledgeItem withStatus(Long id, String status) {
        KnowledgeItem it = new KnowledgeItem();
        it.setId(id);
        it.setTitle("标题");
        it.setStatus(status);
        it.setAuthorId(2L);
        it.setViewCount(0);
        return it;
    }

    @Test
    void publish_moves_draft_to_published_and_stamps_time() {
        when(itemMapper.selectById(1L)).thenReturn(withStatus(1L, "DRAFT"));

        KnowledgeDetailVO vo = knowledgeService.publish(1L);

        assertThat(vo.getStatus()).isEqualTo("PUBLISHED");
        verify(itemMapper).updateById(any(KnowledgeItem.class));
    }

    @Test
    void publish_rejects_already_published() {
        when(itemMapper.selectById(1L)).thenReturn(withStatus(1L, "PUBLISHED"));

        assertThatThrownBy(() -> knowledgeService.publish(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前状态不允许发布");
    }

    @Test
    void publish_rejects_archived_directly() {
        // 归档态必须先 restore 回草稿再发布，避免绕过编辑
        when(itemMapper.selectById(1L)).thenReturn(withStatus(1L, "ARCHIVED"));

        assertThatThrownBy(() -> knowledgeService.publish(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前状态不允许发布");
    }

    @Test
    void archive_from_published_succeeds() {
        when(itemMapper.selectById(1L)).thenReturn(withStatus(1L, "PUBLISHED"));

        assertThat(knowledgeService.archive(1L).getStatus()).isEqualTo("ARCHIVED");
        verify(itemMapper).updateById(any(KnowledgeItem.class));
    }

    @Test
    void archive_from_draft_succeeds() {
        when(itemMapper.selectById(1L)).thenReturn(withStatus(1L, "DRAFT"));

        assertThat(knowledgeService.archive(1L).getStatus()).isEqualTo("ARCHIVED");
    }

    @Test
    void archive_rejects_already_archived() {
        when(itemMapper.selectById(1L)).thenReturn(withStatus(1L, "ARCHIVED"));

        assertThatThrownBy(() -> knowledgeService.archive(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前状态不允许归档");
    }

    @Test
    void restore_moves_archived_to_draft() {
        when(itemMapper.selectById(1L)).thenReturn(withStatus(1L, "ARCHIVED"));

        assertThat(knowledgeService.restore(1L).getStatus()).isEqualTo("DRAFT");
    }

    @Test
    void restore_rejects_published() {
        when(itemMapper.selectById(1L)).thenReturn(withStatus(1L, "PUBLISHED"));

        assertThatThrownBy(() -> knowledgeService.restore(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("当前状态不允许恢复");
    }

    @Test
    void transition_rejects_non_author() {
        KnowledgeItem others = withStatus(1L, "DRAFT");
        others.setAuthorId(99L);
        when(itemMapper.selectById(1L)).thenReturn(others);

        assertThatThrownBy(() -> knowledgeService.publish(1L))
                .isInstanceOf(BusinessException.class)
                .hasMessage("无权操作他人的知识条目");
    }

    @Test
    void admin_can_transition_others_item() {
        loginAs(1L, "ADMIN");
        KnowledgeItem others = withStatus(1L, "DRAFT");
        others.setAuthorId(99L);
        when(itemMapper.selectById(1L)).thenReturn(others);

        assertThat(knowledgeService.publish(1L).getStatus()).isEqualTo("PUBLISHED");
    }
}
