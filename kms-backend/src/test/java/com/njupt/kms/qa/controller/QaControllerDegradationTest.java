package com.njupt.kms.qa.controller;

import com.njupt.kms.common.BusinessException;
import com.njupt.kms.qa.index.IndexingService;
import com.njupt.kms.qa.service.QaHistoryService;
import com.njupt.kms.qa.service.QaService;
import org.junit.jupiter.api.Test;
import org.springframework.http.codec.ServerSentEvent;
import reactor.core.publisher.Flux;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 降级行为：RAG 未配置时，问答接口必须给出可读提示而不是 500。
 * 其余功能（检索、列表等）完全不受影响，与本控制器无关。
 */
class QaControllerDegradationTest {

    /** 一提问就抛「未启用」的假服务。 */
    static class DisabledQaService extends QaService {
        DisabledQaService() {
            super(null, null, null);
        }

        @Override
        public QaAnswer ask(String question, String chatId) {
            throw new BusinessException(
                    "问答功能未启用：请在 application-local.yaml 中配置 app.rag.*");
        }
    }

    /** 返回固定流的假服务，用于验证正常路径的事件序列。 */
    static class WorkingQaService extends QaService {
        WorkingQaService() {
            super(null, null, null);
        }

        @Override
        public QaAnswer ask(String question, String chatId) {
            return new QaAnswer(List.of(), Flux.just("答", "案"));
        }
    }

    private QaController controller(QaService service) {
        return new QaController(service, new QaHistoryService(null, null),
                new IndexingService(null, null, Runnable::run,
                        new com.njupt.kms.qa.config.RagProperties(), id -> null));
    }

    private List<ServerSentEvent<Object>> collect(Flux<ServerSentEvent<Object>> flux) {
        return flux.collectList().block();
    }

    @Test
    void disabled_rag_yields_a_single_error_event_with_a_readable_message() {
        Flux<ServerSentEvent<Object>> flux = controller(new DisabledQaService()).ask("问题", null);

        List<ServerSentEvent<Object>> events = collect(flux);

        assertThat(events).hasSize(1);
        assertThat(events.get(0).event()).isEqualTo("error");
        assertThat(String.valueOf(events.get(0).data()))
                .contains("问答功能未启用")
                .contains("application-local.yaml");
    }

    @Test
    void successful_ask_emits_sources_then_deltas_then_done() {
        Flux<ServerSentEvent<Object>> flux = controller(new WorkingQaService()).ask("问题", "chat-1");

        List<ServerSentEvent<Object>> events = collect(flux);

        assertThat(events).extracting(ServerSentEvent::event)
                .containsExactly("sources", "delta", "delta", "done");
    }

    @Test
    void done_event_carries_the_conversation_id_so_the_client_can_continue_it() {
        Flux<ServerSentEvent<Object>> flux = controller(new WorkingQaService()).ask("问题", "chat-1");

        List<ServerSentEvent<Object>> events = collect(flux);
        ServerSentEvent<Object> done = events.get(events.size() - 1);

        assertThat(String.valueOf(done.data())).contains("chat-1");
    }

    @Test
    void generated_conversation_id_is_returned_when_client_omits_it() {
        Flux<ServerSentEvent<Object>> flux = controller(new WorkingQaService()).ask("问题", null);

        List<ServerSentEvent<Object>> events = collect(flux);
        ServerSentEvent<Object> done = events.get(events.size() - 1);

        // 未传 chatId 时必须回传一个，否则前端无法继续该会话
        assertThat(String.valueOf(done.data())).contains("chatId").containsPattern("[0-9a-f]{32}");
    }

    @Test
    void stream_error_is_reported_as_an_error_event_not_a_broken_connection() {
        QaService failing = new WorkingQaService() {
            @Override
            public QaAnswer ask(String question, String chatId) {
                return new QaAnswer(List.of(), Flux.error(new IllegalStateException("上游超时")));
            }
        };

        List<ServerSentEvent<Object>> events = collect(controller(failing).ask("问题", null));

        assertThat(events).extracting(ServerSentEvent::event).contains("error");
        // 内部异常细节不外泄
        assertThat(String.valueOf(events.get(events.size() - 1).data())).doesNotContain("上游超时");
    }

    @Test
    void status_reports_disabled_when_rag_is_off() {
        var result = controller(new WorkingQaService()).status();

        assertThat(result.getCode()).isZero();
        assertThat(result.getData().enabled()).isFalse();
        assertThat(result.getData().indexedItems()).isZero();
    }
}
