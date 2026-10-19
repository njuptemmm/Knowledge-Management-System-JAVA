package com.njupt.kms.qa.service;

import com.njupt.kms.qa.config.QaPrompts;
import com.njupt.kms.qa.dto.QaMessage;
import com.njupt.kms.qa.dto.SourceRef;
import com.njupt.kms.qa.retrieve.RetrievalService;
import com.njupt.kms.qa.retrieve.RetrievedChunk;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.client.ChatClient.ChatClientRequestSpec;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.regex.Pattern;

@Slf4j
@Service
@RequiredArgsConstructor
public class QaService {

    /** 喂回模型的最近消息条数（一问一答算 2 条），约等于最近 4 轮。 */
    private static final int MEMORY_MESSAGES = 8;
    /**
     * 单条历史消息的最大字符数。
     *
     * <p>历史只用来消解代词和省略（「它的缺点呢？」），不需要完整长答案；
     * 不截断的话，几轮长回答就能把上下文预算吃掉一大半，挤掉真正要紧的检索资料。
     */
    private static final int MEMORY_MESSAGE_MAX_CHARS = 800;
    /** 超过这个长度的问题按「完整提问」处理，不再当作追问改写检索词。 */
    private static final int FOLLOW_UP_MAX_CHARS = 30;
    private static final int PREVIOUS_QUESTION_MAX_CHARS = 200;
    /** 指代词。命中任一即视为追问，需要补上一轮主题。 */
    private static final Pattern FOLLOW_UP_PATTERN =
            Pattern.compile("它|他|她|这个|那个|该|上面|前面|刚才|这种|那种|此");

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
        // 先取历史，再写入本轮问题：顺序反了会把当前问题重复喂一遍
        List<QaMessage> prior = historyService.recent(chatId, MEMORY_MESSAGES);

        // 检索用的查询可能和原问题不同：追问里的「它」单独拿去向量化什么都召不回，
        // 必须先把上一轮的主题补进来（详见 retrievalQuery）
        // 可见性由 RetrievalService 内部按 CurrentUser 判定
        List<RetrievedChunk> chunks = retrievalService.retrieve(retrievalQuery(question, prior));
        List<SourceRef> sources = chunks.stream()
                .map(chunk -> new SourceRef(chunk.itemId(), chunk.title(), chunk.headingPath(), chunk.score()))
                .toList();

        List<Message> memory = prior.stream()
                .filter(m -> StringUtils.hasText(m.content()))
                .map(QaService::toMessage)
                .toList();

        historyService.append(chatId, QaMessage.user(question));

        ChatClientRequestSpec request = qaChatClient.prompt();
        if (!memory.isEmpty()) {
            // 历史按原始角色放回去，模型才能把「它」「这个方案」对上号
            request = request.messages(memory);
        }

        StringBuilder collected = new StringBuilder();
        Flux<String> stream = request
                .user(QaPrompts.buildUserPrompt(question, chunks))
                .stream()
                .content()
                .doOnNext(collected::append)
                .doOnComplete(() -> historyService.append(chatId,
                        QaMessage.assistant(collected.toString(), sources)))
                .doOnError(e -> log.error("[RAG] 生成失败 chatId={}", chatId, e));

        return new QaAnswer(sources, stream);
    }

    /**
     * 构造检索查询词。
     *
     * <p>追问（「它有什么缺点？」）单独拿去向量化时，句子里没有任何可检索的实体，
     * 召回结果为空，模型会按防幻觉规则回答「知识库中没有相关内容」——
     * 记忆把上下文带给了模型，却带不到检索器，多轮问答就断在这里（实测踩过）。
     *
     * <p>判定为追问时，把上一句用户提问拼到当前问题前面再检索。
     * 只在**看着像追问**时才拼接，而不是无脑拼：
     * 同一会话里换话题时，硬拼上一轮主题会把检索带偏。
     */
    private static String retrievalQuery(String question, List<QaMessage> prior) {
        if (prior.isEmpty() || question.length() > FOLLOW_UP_MAX_CHARS
                || !FOLLOW_UP_PATTERN.matcher(question).find()) {
            return question;
        }
        for (int i = prior.size() - 1; i >= 0; i--) {
            QaMessage message = prior.get(i);
            if ("user".equals(message.role()) && StringUtils.hasText(message.content())) {
                String previous = message.content().strip();
                if (previous.length() > PREVIOUS_QUESTION_MAX_CHARS) {
                    previous = previous.substring(0, PREVIOUS_QUESTION_MAX_CHARS);
                }
                return previous + " " + question;
            }
        }
        return question;
    }

    private static Message toMessage(QaMessage message) {
        String content = message.content();
        if (content.length() > MEMORY_MESSAGE_MAX_CHARS) {
            content = content.substring(0, MEMORY_MESSAGE_MAX_CHARS) + "……";
        }
        return "assistant".equals(message.role())
                ? new AssistantMessage(content)
                : new UserMessage(content);
    }

    /** 引用来源 + 答案增量流。 */
    public record QaAnswer(List<SourceRef> sources, Flux<String> stream) {
    }
}
