package com.njupt.kms.qa.controller;

import com.njupt.kms.common.BusinessException;
import com.njupt.kms.common.Result;
import com.njupt.kms.common.ResultCode;
import com.njupt.kms.qa.dto.QaMessage;
import com.njupt.kms.qa.dto.QaSessionVO;
import com.njupt.kms.qa.dto.QaStatusVO;
import com.njupt.kms.qa.index.IndexStatus;
import com.njupt.kms.qa.index.IndexingService;
import com.njupt.kms.qa.service.QaHistoryService;
import com.njupt.kms.qa.service.QaService;
import com.njupt.kms.qa.service.QaSessionService;
import com.njupt.kms.security.CurrentUser;
import com.njupt.kms.qa.dto.QaAskRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
    private final QaSessionService sessionService;
    private final IndexingService indexingService;

    /**
     * 流式问答。公开接口——未登录也能提问，但只能基于已发布内容。
     *
     * <p>用 POST + 请求体而非 GET：问题可以很长，中文经 URL 编码后长度膨胀数倍，
     * 放在查询参数里会撞上 URL 长度上限并返回 400（实测 1500 字即触发）。
     * 前端本来也必须用 fetch 而不是 EventSource（后者无法带 Authorization 头），
     * 因此 POST 不带来额外成本。
     *
     * <p>错误一律以 {@code error} 事件结束流，而不是抛出异常：
     * SSE 响应一旦开始，抛异常只会让前端拿到一个断掉的连接，拿不到原因。
     */
    @PostMapping(value = "/ask", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> ask(@Valid @RequestBody QaAskRequest request) {

        String question = request.getQuestion();
        String conversationId = StringUtils.hasText(request.getChatId())
                ? request.getChatId()
                : UUID.randomUUID().toString().replace("-", "");

        try {
            // 续聊别人的会话必须被拒绝；新建会话时元数据为空，检查直接放行
            sessionService.checkAccess(conversationId);

            // 先登记会话（归属 + 标题），再开始生成：
            // 生成阶段可能失败，但会话本身已经产生，用户应该能在列表里看到它
            sessionService.touch(conversationId, question);

            // 可见性判定发生在 RetrievalService 内部（按 CurrentUser）
            QaService.QaAnswer answer = qaService.ask(question, conversationId);

            Flux<ServerSentEvent<Object>> sources = Flux.just(
                    ServerSentEvent.builder().event("sources").data(answer.sources()).build());

            // delta 必须包成 JSON 对象再发，不能直接丢裸字符串。
            // 换行在 SSE 里是字段分隔符：模型输出的 "\n" 会被写成空 data 行，
            // 客户端无法区分「一个换行」和「没有内容」，换行与空格就这样被静默丢掉——
            // 实测一段含 6 个换行的 Markdown 列表，经 SSE 传出去后换行数变成 0，
            // 渲染出来就是一整段连在一起的文字。JSON 编码会把换行转义成 \n，安全穿过 SSE。
            Flux<ServerSentEvent<Object>> deltas = answer.stream()
                    .map(token -> ServerSentEvent.builder().event("delta")
                            .data(Map.of("text", token)).build());

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

    /** 当前用户的会话列表，按最后活跃时间倒序。未登录返回空列表。 */
    @GetMapping("/sessions")
    public Result<List<QaSessionVO>> sessions() {
        return Result.ok(sessionService.list());
    }

    /** 删除会话：元数据与消息一并清除。 */
    @DeleteMapping("/sessions/{chatId}")
    public Result<Void> deleteSession(@PathVariable String chatId) {
        sessionService.checkAccess(chatId);
        sessionService.delete(chatId);
        return Result.ok();
    }

    @GetMapping("/history/{chatId}")
    public Result<List<QaMessage>> history(@PathVariable String chatId) {
        sessionService.checkAccess(chatId);
        return Result.ok(historyService.history(chatId));
    }

    @DeleteMapping("/history/{chatId}")
    public Result<Void> clearHistory(@PathVariable String chatId) {
        sessionService.checkAccess(chatId);
        historyService.clear(chatId);
        return Result.ok();
    }

    /** 公开只读：前端据此决定是否显示问答入口。 */
    @GetMapping("/status")
    public Result<QaStatusVO> status() {
        return Result.ok(toVO(indexingService.status()));
    }

    /**
     * 全量重建索引。仅管理员。
     *
     * <p>耗时较长（并发下 500 块约 25 秒），且会打满向量服务配额。
     * 前端只在管理员菜单里显示入口，但权限不能只靠前端隐藏——
     * 任何登录用户都能直接发请求，所以后端必须再判一次角色。
     */
    @PostMapping("/reindex")
    public Result<QaStatusVO> reindex() {
        if (!CurrentUser.getRequired().isAdmin()) {
            throw new BusinessException(ResultCode.FORBIDDEN, "只有管理员可以重建索引");
        }
        return Result.ok(toVO(indexingService.rebuildAll()));
    }

    private QaStatusVO toVO(IndexStatus status) {
        return new QaStatusVO(status.enabled(), status.indexedItems(),
                status.indexedChunks(), status.lastRebuildAt());
    }
}
