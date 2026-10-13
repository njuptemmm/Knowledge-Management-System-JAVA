package com.njupt.kms.qa.controller;

import com.njupt.kms.common.BusinessException;
import com.njupt.kms.common.Result;
import com.njupt.kms.qa.dto.QaMessage;
import com.njupt.kms.qa.dto.QaStatusVO;
import com.njupt.kms.qa.index.IndexStatus;
import com.njupt.kms.qa.index.IndexingService;
import com.njupt.kms.qa.service.QaHistoryService;
import com.njupt.kms.qa.service.QaService;
import com.njupt.kms.security.CurrentUser;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 智能问答接口。
 *
 * <p>SSE 事件协议见 {@code docs/API.md}：sources → delta×N → done，出错时为 error。
 */
@Slf4j
@Validated
@RestController
@RequestMapping("/api/qa")
@RequiredArgsConstructor
public class QaController {

    private final QaService qaService;
    private final QaHistoryService historyService;
    private final IndexingService indexingService;

    /**
     * 流式问答。公开接口——未登录也能提问，但只能基于已发布内容。
     *
     * <p>错误一律以 {@code error} 事件结束流，而不是抛出异常：
     * SSE 响应一旦开始，抛异常只会让前端拿到一个断掉的连接，拿不到原因。
     */
    @GetMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> ask(
            @RequestParam("question") @NotBlank String question,
            @RequestParam(value = "chatId", required = false) String chatId) {

        String conversationId = (chatId == null || chatId.isBlank())
                ? UUID.randomUUID().toString().replace("-", "")
                : chatId;

        try {
            // 可见性判定发生在 RetrievalService 内部（按 CurrentUser）
            QaService.QaAnswer answer = qaService.ask(question, conversationId);

            Flux<ServerSentEvent<Object>> sources = Flux.just(
                    ServerSentEvent.builder().event("sources").data(answer.sources()).build());

            Flux<ServerSentEvent<Object>> deltas = answer.stream()
                    .map(token -> ServerSentEvent.builder().event("delta").data(token).build());

            Flux<ServerSentEvent<Object>> done = Flux.just(
                    ServerSentEvent.builder().event("done")
                            .data(Map.of("chatId", conversationId)).build());

            return Flux.concat(sources, deltas, done)
                    .onErrorResume(e -> errorEvent(e, "生成失败，请稍后重试"));

        } catch (BusinessException e) {
            // 召回阶段就失败：RAG 未启用、问题为空等，直接以 error 事件结束
            return Flux.just(ServerSentEvent.builder().event("error")
                    .data(Map.of("message", e.getMessage())).build());
        } catch (Exception e) {
            return errorEvent(e, "问答服务异常，请稍后重试");
        }
    }

    private Flux<ServerSentEvent<Object>> errorEvent(Throwable e, String fallbackMessage) {
        log.error("[RAG] 流式问答失败", e);
        String message = (e instanceof BusinessException) ? e.getMessage() : fallbackMessage;
        return Flux.just(ServerSentEvent.builder().event("error")
                .data(Map.of("message", message)).build());
    }

    @GetMapping("/history/{chatId}")
    public Result<List<QaMessage>> history(@PathVariable String chatId) {
        return Result.ok(historyService.history(chatId));
    }

    @DeleteMapping("/history/{chatId}")
    public Result<Void> clearHistory(@PathVariable String chatId) {
        historyService.clear(chatId);
        return Result.ok();
    }

    /** 公开只读：前端据此决定是否显示问答入口。 */
    @GetMapping("/status")
    public Result<QaStatusVO> status() {
        return Result.ok(toVO(indexingService.status()));
    }

    /**
     * 全量重建索引。需登录。
     *
     * <p>耗时较长（并发下 500 块约 25 秒），前端需要给出明确的等待反馈。
     */
    @PostMapping("/reindex")
    public Result<QaStatusVO> reindex() {
        CurrentUser.getRequired();
        return Result.ok(toVO(indexingService.rebuildAll()));
    }

    private QaStatusVO toVO(IndexStatus status) {
        return new QaStatusVO(status.enabled(), status.indexedItems(),
                status.indexedChunks(), status.lastRebuildAt());
    }
}
