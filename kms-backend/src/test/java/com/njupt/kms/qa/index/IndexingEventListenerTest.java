package com.njupt.kms.qa.index;

import com.njupt.kms.common.event.KnowledgeIndexEvent;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

class IndexingEventListenerTest {

    /** 记录调用的假索引服务。 */
    static class RecordingIndexer extends IndexingService {
        final List<Long> indexed = new ArrayList<>();
        final List<Long> removed = new ArrayList<>();
        boolean fail;

        RecordingIndexer() {
            super(null, null, Runnable::run, new com.njupt.kms.qa.config.RagProperties(), id -> null);
        }

        @Override
        public void indexItem(Long itemId) {
            if (fail) {
                throw new IllegalStateException("embedding 服务不可用");
            }
            indexed.add(itemId);
        }

        @Override
        public void removeItem(Long itemId) {
            removed.add(itemId);
        }
    }

    @Test
    void upsert_event_triggers_reindex() {
        RecordingIndexer indexer = new RecordingIndexer();

        new IndexingEventListener(indexer).onKnowledgeChanged(KnowledgeIndexEvent.upsert(9L));

        assertThat(indexer.indexed).containsExactly(9L);
        assertThat(indexer.removed).isEmpty();
    }

    @Test
    void remove_event_deletes_from_index() {
        RecordingIndexer indexer = new RecordingIndexer();

        new IndexingEventListener(indexer).onKnowledgeChanged(KnowledgeIndexEvent.remove(9L));

        assertThat(indexer.removed).containsExactly(9L);
        assertThat(indexer.indexed).isEmpty();
    }

    @Test
    void listener_never_propagates_exceptions() {
        // 索引失败不能影响主业务：条目该发布还得发布
        RecordingIndexer indexer = new RecordingIndexer();
        indexer.fail = true;

        assertThatCode(() -> new IndexingEventListener(indexer)
                .onKnowledgeChanged(KnowledgeIndexEvent.upsert(9L)))
                .doesNotThrowAnyException();
    }

    @Test
    void null_event_is_ignored() {
        RecordingIndexer indexer = new RecordingIndexer();

        assertThatCode(() -> new IndexingEventListener(indexer).onKnowledgeChanged(null))
                .doesNotThrowAnyException();
        assertThat(indexer.indexed).isEmpty();
        assertThat(indexer.removed).isEmpty();
    }

    @Test
    void event_without_item_id_is_ignored() {
        RecordingIndexer indexer = new RecordingIndexer();

        assertThatCode(() -> new IndexingEventListener(indexer)
                .onKnowledgeChanged(new KnowledgeIndexEvent(null, KnowledgeIndexEvent.Action.UPSERT)))
                .doesNotThrowAnyException();
        assertThat(indexer.indexed).isEmpty();
    }
}
