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
 * Java 代码分块器。
 *
 * <p>该分块器用于处理 Java 源码类型的 Evidence。它会优先根据 Java 类型声明
 * 和方法声明进行结构化分块，使每个 chunk 尽量对应一个类、接口、枚举、record
 * 或方法片段。</p>
 *
 * <p>如果无法识别出有效的 Java 结构，则会退化为按固定行数分块，避免整份代码
 * 被作为一个过大的 chunk 处理。</p>
 */
@Order(10)
@Component
public class JavaCodeChunker extends ChunkSupport implements EvidenceChunker {

    /**
     * Java 类型声明匹配模式。
     *
     * <p>用于识别 class、interface、enum、record 等类型声明，并提取类型名称。</p>
     */
    private static final Pattern TYPE_DECLARATION = Pattern.compile(
            "\\b(class|interface|enum|record)\\s+([A-Za-z_$][A-Za-z\\d_$]*)\\b");

    /**
     * Java 方法声明匹配模式。
     *
     * <p>用于识别常见的方法声明，并提取方法名。</p>
     *
     * <p>该正则主要覆盖一行内完成的方法声明，例如：</p>
     * <pre>
     * public String getName() {
     * protected void doWork() throws Exception {
     * </pre>
     */
    private static final Pattern METHOD_DECLARATION = Pattern.compile(
            "^\\s*(public|protected|private|static|final|native|synchronized|abstract|default|\\s)+[\\w<>\\[\\], ?]+\\s+([A-Za-z_$][A-Za-z\\d_$]*)\\s*\\([^;]*\\)\\s*(throws\\s+[\\w.,\\s]+)?\\{\\s*$");

    /**
     * 无法识别 Java 结构时的兜底分块行数。
     */
    private static final int MAX_FALLBACK_LINES = 120;

    /**
     * 判断当前分块器是否支持指定 Evidence。
     *
     * <p>满足以下任一条件即认为支持：</p>
     * <ul>
     *     <li>Evidence 类型为 JAVA_CODE</li>
     *     <li>文件路径以 .java 结尾</li>
     * </ul>
     *
     * @param evidence 待判断的 Evidence
     * @return 如果该 Evidence 是 Java 代码则返回 true，否则返回 false
     */
    @Override
    public boolean supports(Evidence evidence) {
        // filePath 可能为空，这里统一转为空字符串，避免空指针异常
        String filePath = evidence.getFilePath() == null ? "" : evidence.getFilePath().toLowerCase();

        return evidence.getEvidenceType() == EvidenceType.JAVA_CODE || filePath.endsWith(".java");
    }

    /**
     * 将 Java 源码 Evidence 拆分为多个 EvidenceChunk。
     *
     * <p>分块流程如下：</p>
     * <ol>
     *     <li>将源码按行拆分</li>
     *     <li>扫描每一行，识别类型声明和方法声明</li>
     *     <li>将识别到的声明位置作为 chunk 起点</li>
     *     <li>根据相邻起点计算 chunk 的结束行</li>
     *     <li>调用公共方法构建 EvidenceChunk</li>
     * </ol>
     *
     * <p>如果没有识别到类型或方法声明，则使用固定行数策略兜底分块。</p>
     *
     * @param evidence 原始 Java 代码 Evidence
     * @return Java 代码分块后的 EvidenceChunk 列表
     */
    @Override
    public List<EvidenceChunk> chunk(Evidence evidence) {
        String[] lines = lines(evidence.getContent());

        // 空内容不生成任何 chunk
        if (lines.length == 0) {
            return List.of();
        }

        List<RangeStart> starts = new ArrayList<>();

        // 默认从第 1 行开始，保留 Evidence 原始标题和符号名
        starts.add(new RangeStart(1, evidence.getTitle(), evidence.getSymbolName()));

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];

            // 优先识别 class / interface / enum / record 等类型声明
            Matcher typeMatcher = TYPE_DECLARATION.matcher(line);
            if (typeMatcher.find()) {
                starts.add(new RangeStart(i + 1, typeMatcher.group(2), typeMatcher.group(2)));
                continue;
            }

            // 识别方法声明，并排除 if / for / while 等控制语句误匹配
            Matcher methodMatcher = METHOD_DECLARATION.matcher(line);
            if (methodMatcher.find() && !isControlStatement(methodMatcher.group(2))) {
                starts.add(new RangeStart(i + 1, methodMatcher.group(2), methodMatcher.group(2)));
            }
        }

        // 如果没有识别到任何结构化起点，则退化为按固定行数分块
        if (starts.size() <= 1) {
            return buildChunks(evidence, fixedRanges(evidence.getContent(), MAX_FALLBACK_LINES));
        }

        List<Range> ranges = new ArrayList<>();

        for (int i = 0; i < starts.size(); i++) {
            RangeStart start = starts.get(i);

            // 当前 chunk 的结束行为下一个结构起点的前一行；最后一个 chunk 直到文件末尾
            int end = i + 1 < starts.size() ? starts.get(i + 1).line() - 1 : lines.length;

            ranges.add(new Range(start.line(), end, start.title(), start.symbolName()));
        }

        return buildChunks(evidence, ranges);
    }

    /**
     * 判断识别出的符号是否为 Java 控制语句。
     *
     * <p>该方法用于避免方法声明正则将 if、for、while、switch、catch 等控制结构
     * 误判为方法声明。</p>
     *
     * @param symbol 正则匹配出的符号名称
     * @return 如果是控制语句则返回 true，否则返回 false
     */
    private boolean isControlStatement(String symbol) {
        return "if".equals(symbol) || "for".equals(symbol) || "while".equals(symbol)
                || "switch".equals(symbol) || "catch".equals(symbol);
    }

    /**
     * 表示一个 chunk 起始位置。
     *
     * <p>扫描 Java 源码时，每识别到一个类型声明或方法声明，就会生成一个 RangeStart。
     * 后续会根据相邻 RangeStart 计算完整的 Range。</p>
     *
     * @param line       起始行号，1-based
     * @param title      当前 chunk 的标题，通常为类型名或方法名
     * @param symbolName 当前 chunk 对应的符号名，通常为类型名或方法名
     */
    private record RangeStart(int line, String title, String symbolName) {
    }
}