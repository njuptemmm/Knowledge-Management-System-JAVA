package com.njupt.kms.qa.service;

import com.njupt.kms.qa.config.QaPrompts;
import com.njupt.kms.qa.dto.QaMessage;
import com.njupt.kms.qa.dto.SourceRef;
import com.njupt.kms.qa.retrieve.RetrievalService;
import com.njupt.kms.qa.retrieve.RetrievedChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class QaService {

    private final RetrievalService retrievalService;
    private final QaHistoryService historyService;
    private final ChatClient qaChatClient;

    /**
     * 一次问答。返回引用来源与答案增量流。
     *
     * <p>刻意先做召回、再开始流式生成：调用方可以立刻把「引用来源」推给前端，
     * 用户马上看到系统找到了哪些资料，再等答案逐字出现。
     */
    public QaAnswer ask(String question, String chatId) {
        // 可见性由 RetrievalService 内部按 CurrentUser 判定
        List<RetrievedChunk> chunks = retrievalService.retrieve(question);
        List<SourceRef> sources = chunks.stream()
                .map(chunk -> new SourceRef(chunk.itemId(), chunk.title(), chunk.headingPath(), chunk.score()))
                .toList();

        historyService.append(chatId, QaMessage.user(question));

        StringBuilder collected = new StringBuilder();
        Flux<String> stream = qaChatClient.prompt()
                .user(QaPrompts.buildUserPrompt(question, chunks))
                .stream()
                .content()
                .doOnNext(collected::append)
                .doOnComplete(() -> historyService.append(chatId,
                        QaMessage.assistant(collected.toString(), sources)))
                .doOnError(e -> log.error("[RAG] 生成失败 chatId={}", chatId, e));

        return new QaAnswer(sources, stream);
    }

    /** 引用来源 + 答案增量流。 */
    public record QaAnswer(List<SourceRef> sources, Flux<String> stream) {
    }
}
