package com.njupt.kms.qa.chunk;

import com.njupt.kms.qa.config.RagProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 切块算法：Markdown 文本 → 适合向量化的块列表。
 *
 * <p>两步走：先按 Markdown 标题切成「节」（保留语义边界，并记录标题路径），
 * 超长的节再按长度切分并保留重叠（避免语义在切点处断裂）。
 *
 * <p>纯计算，不依赖 Spring 容器以外的任何东西，便于独立测试。
 */
@Service
public class ChunkingService {

    private static final Pattern HEADING = Pattern.compile("^(#{1,6})\\s+(.*)$");

    /** 断点候选，按优先级排列。中文句末标点在前，英文在后。 */
    private static final List<String> BREAK_MARKERS = List.of("。", "！", "？", "；", "\n", ". ");

    private final int maxLength;
    private final int overlap;

    /**
     * 生产用构造器。显式标注 {@code @Autowired}：本类还有一个供测试用的
     * {@code (int, int)} 构造器，不标的话 Spring 无法决定用哪个。
     */
    @Autowired
    public ChunkingService(RagProperties properties) {
        this(properties.getChunk().getMaxLength(), properties.getChunk().getOverlap());
    }

    /** 测试用构造器：直接给定长度参数，无需构造 RagProperties。 */
    public ChunkingService(int maxLength, int overlap) {
        // overlap 必须小于 maxLength，否则会原地打转
        this.maxLength = Math.max(1, maxLength);
        this.overlap = Math.max(0, Math.min(overlap, this.maxLength / 2));
    }

    /**
     * 把一篇文档切成块。
     *
     * @param itemId 所属条目 id
     * @param text   Markdown 正文
     * @param title  文档标题，作为没有标题结构时的路径
     */
    public List<Chunk> split(Long itemId, String text, String title) {
        List<Chunk> chunks = new ArrayList<>();
        if (!StringUtils.hasText(text)) {
            return chunks;
        }

        for (Section section : splitIntoSections(text, title)) {
            for (String piece : splitByLength(section.body())) {
                if (StringUtils.hasText(piece)) {
                    chunks.add(new Chunk(itemId, chunks.size(), piece.strip(), section.headingPath()));
                }
            }
        }
        return chunks;
    }

    /** 一节：标题路径 + 正文。 */
    private record Section(String headingPath, String body) {
    }

    /**
     * 按 Markdown 标题切节，维护标题栈以生成路径。
     *
     * <p>遇到同级或更高级的标题时，栈中不低于它的层级被弹出。
     * 这保证了 {@code # A / ## B / # C} 得到路径 {@code A > B} 与 {@code C}，
     * 而不是错误的 {@code A > B > C}。
     */
    private List<Section> splitIntoSections(String text, String title) {
        List<Section> sections = new ArrayList<>();
        Deque<String> headingStack = new ArrayDeque<>();
        List<Integer> levelStack = new ArrayList<>();

        String currentPath = StringUtils.hasText(title) ? title.strip() : "正文";
        StringBuilder buffer = new StringBuilder();

        for (String line : text.split("\r?\n", -1)) {
            Matcher matcher = HEADING.matcher(line.strip());
            if (matcher.matches()) {
                flush(sections, currentPath, buffer);

                int level = matcher.group(1).length();
                String heading = matcher.group(2).strip();

                while (!levelStack.isEmpty() && levelStack.get(levelStack.size() - 1) >= level) {
                    levelStack.remove(levelStack.size() - 1);
                    headingStack.removeLast();
                }
                levelStack.add(level);
                headingStack.addLast(heading);
                currentPath = String.join(" > ", headingStack);
            } else {
                buffer.append(line).append('\n');
            }
        }
        flush(sections, currentPath, buffer);
        return sections;
    }

    private void flush(List<Section> sections, String path, StringBuilder buffer) {
        String body = buffer.toString().strip();
        if (!body.isEmpty()) {
            sections.add(new Section(path, body));
        }
        buffer.setLength(0);
    }

    /**
     * 按长度切分，保留重叠。
     *
     * <p>每次切完，下一块的起点回退 {@code overlap} 个字符，
     * 使相邻块共享一段上下文，避免一句话被切成两半后两边都读不出意思。
     */
    private List<String> splitByLength(String text) {
        List<String> pieces = new ArrayList<>();
        String remaining = text;

        while (remaining.length() > maxLength) {
            int cut = findBreakPoint(remaining, maxLength);
            pieces.add(remaining.substring(0, cut));

            int nextStart = cut - overlap;
            if (nextStart <= 0) {
                nextStart = cut;   // 防御：overlap 异常时也不能原地不动
            }
            remaining = remaining.substring(nextStart);
        }
        if (!remaining.isBlank()) {
            pieces.add(remaining);
        }
        return pieces;
    }

    /** 在 (0, limit] 内找一个尽量自然的断点，找不到就硬切。 */
    private int findBreakPoint(String text, int limit) {
        int paragraph = text.lastIndexOf("\n\n", limit);
        if (paragraph > limit / 2) {
            return paragraph + 2;
        }
        for (String marker : BREAK_MARKERS) {
            int index = text.lastIndexOf(marker, limit);
            if (index > limit / 2) {
                return index + marker.length();
            }
        }
        return limit;
    }
}
