package com.codeagent.rag.chunk;

import com.codeagent.rag.model.Evidence;
import com.codeagent.rag.model.EvidenceChunk;
import com.codeagent.rag.model.EvidenceType;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Markdown 文档分块器。
 *
 * <p>该分块器用于处理 Markdown 或普通文档类型的 Evidence。
 * 它会优先根据 Markdown 标题进行结构化分块，使每个 chunk 尽量对应一个章节。</p>
 *
 * <p>支持的标题格式包括 Markdown ATX 标题，例如：</p>
 * <pre>
 * # 一级标题
 * ## 二级标题
 * ### 三级标题
 * </pre>
 *
 * <p>如果文档中没有识别到 Markdown 标题，则会退化为按固定行数分块，
 * 避免长文档被整体作为一个过大的 chunk。</p>
 */
@Order(20)
@Component
public class MarkdownChunker extends ChunkSupport implements EvidenceChunker {

    /**
     * Markdown 标题匹配模式。
     *
     * <p>用于识别 1 到 6 级 ATX 标题，并提取标题文本。</p>
     *
     * <p>该正则支持标题前最多 3 个空格，兼容 Markdown 标准中的缩进规则；
     * 同时支持行尾可选的关闭符号，例如 {@code ## 标题 ##}。</p>
     */
    private static final Pattern HEADING = Pattern.compile("^\\s{0,3}(#{1,6})\\s+(.+?)\\s*#*\\s*$");

    /**
     * 无法识别 Markdown 标题时的兜底分块行数。
     */
    private static final int MAX_FALLBACK_LINES = 100;

    /**
     * 判断当前分块器是否支持指定 Evidence。
     *
     * <p>满足以下任一条件即认为支持：</p>
     * <ul>
     *     <li>Evidence 类型为 MARKDOWN</li>
     *     <li>Evidence 类型为 DOCUMENT</li>
     *     <li>文件路径以 .md、.mdx 或 .markdown 结尾</li>
     * </ul>
     *
     * @param evidence 待判断的 Evidence
     * @return 如果该 Evidence 可作为 Markdown/文档处理则返回 true，否则返回 false
     */
    @Override
    public boolean supports(Evidence evidence) {
        // filePath 可能为空，这里统一转为空字符串，避免空指针异常
        String filePath = evidence.getFilePath() == null ? "" : evidence.getFilePath().toLowerCase();

        return evidence.getEvidenceType() == EvidenceType.MARKDOWN
                || evidence.getEvidenceType() == EvidenceType.DOCUMENT
                || filePath.endsWith(".md")
                || filePath.endsWith(".mdx")
                || filePath.endsWith(".markdown");
    }

    /**
     * 将 Markdown 或文档 Evidence 拆分为多个 EvidenceChunk。
     *
     * <p>分块流程如下：</p>
     * <ol>
     *     <li>将文档内容按行拆分</li>
     *     <li>扫描每一行，识别 Markdown 标题</li>
     *     <li>将每个标题所在行作为一个 chunk 的起点</li>
     *     <li>根据相邻标题位置计算每个 chunk 的结束行</li>
     *     <li>调用公共方法构建 EvidenceChunk</li>
     * </ol>
     *
     * <p>如果没有识别到标题，则使用固定行数策略兜底分块。</p>
     *
     * @param evidence 原始 Markdown 或文档 Evidence
     * @return 分块后的 EvidenceChunk 列表
     */
    @Override
    public List<EvidenceChunk> chunk(Evidence evidence) {
        String[] lines = lines(evidence.getContent());

        // 空内容不生成任何 chunk
        if (lines.length == 0) {
            return List.of();
        }

        List<RangeStart> starts = new ArrayList<>();

        // 默认从第 1 行开始，保留 Evidence 原始标题
        starts.add(new RangeStart(1, evidence.getTitle()));

        for (int i = 0; i < lines.length; i++) {
            Matcher matcher = HEADING.matcher(lines[i]);

            // 识别 Markdown 标题，并将标题所在行作为新的分块起点
            if (matcher.matches()) {
                starts.add(new RangeStart(i + 1, matcher.group(2)));
            }
        }

        // 如果没有识别到任何标题，则退化为按固定行数分块
        if (starts.size() <= 1) {
            return buildChunks(evidence, fixedRanges(evidence.getContent(), MAX_FALLBACK_LINES));
        }

        List<Range> ranges = new ArrayList<>();

        for (int i = 0; i < starts.size(); i++) {
            RangeStart start = starts.get(i);

            // 当前 chunk 的结束行为下一个标题起点的前一行；最后一个 chunk 直到文档末尾
            int end = i + 1 < starts.size() ? starts.get(i + 1).line() - 1 : lines.length;

            // Markdown 分块中 title 和 symbolName 均使用标题文本
            ranges.add(new Range(start.line(), end, start.title(), start.title()));
        }

        return buildChunks(evidence, ranges);
    }

    /**
     * 表示一个 Markdown 分块起始位置。
     *
     * <p>扫描文档时，每识别到一个标题，就会生成一个 RangeStart。
     * 后续会根据相邻 RangeStart 计算完整的 Range。</p>
     *
     * @param line  起始行号，1-based
     * @param title 当前 chunk 的标题，通常为 Markdown 标题文本
     */
    private record RangeStart(int line, String title) {
    }
}