package com.codeagent.rag.chunk;

import com.codeagent.rag.model.Evidence;
import com.codeagent.rag.model.EvidenceChunk;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 分块器公共支持类。
 *
 * <p>该类封装了不同分块策略中通用的逻辑，例如：</p>
 * <ul>
 *     <li>根据行号范围构建 EvidenceChunk</li>
 *     <li>将文本按行拆分</li>
 *     <li>生成固定行数的分块范围</li>
 * </ul>
 *
 * <p>具体的分块实现类可以继承该类，专注于生成不同的 Range 列表，
 * 再复用 {@link #buildChunks(Evidence, List)} 完成最终 EvidenceChunk 构建。</p>
 */
abstract class ChunkSupport {

    /**
     * 根据 Evidence 原始内容和分块行范围，构建 EvidenceChunk 列表。
     *
     * <p>该方法会保留 Evidence 中的上下文信息，例如项目、分支、提交、来源、文件路径等，
     * 并将每个 Range 对应的文本片段封装为一个 EvidenceChunk。</p>
     *
     * @param evidence 原始证据对象，包含完整文本和元数据
     * @param ranges   分块范围列表，每个 Range 表示一个 chunk 的起止行及可选标题/符号名
     * @return 构建完成的 EvidenceChunk 列表
     */
    protected List<EvidenceChunk> buildChunks(Evidence evidence, List<Range> ranges) {
        // 将完整内容拆分为行数组，后续 Range 使用 1-based 行号进行截取
        String[] lines = lines(evidence.getContent());

        List<EvidenceChunk> chunks = new ArrayList<>();
        int index = 0;

        for (Range range : ranges) {
            // 修正起止行，避免 Range 越界
            int start = Math.max(1, range.startLine());
            int end = Math.min(lines.length, range.endLine());

            // 无效范围直接跳过
            if (start > end) {
                continue;
            }

            // 根据起止行拼接 chunk 内容
            String content = joinLines(lines, start, end);

            // 空白内容不生成 chunk
            if (content.isBlank()) {
                continue;
            }

            chunks.add(EvidenceChunk.builder()
                    .evidenceId(evidence.getEvidenceId())
                    .projectKey(evidence.getProjectKey())
                    .branch(evidence.getBranch())
                    .commitId(evidence.getCommitId())
                    .buildId(evidence.getBuildId())
                    .evidenceType(evidence.getEvidenceType())
                    .sourceSystem(evidence.getSourceSystem())
                    .sourceUrl(evidence.getSourceUrl())
                    .filePath(evidence.getFilePath())

                    // Range 中的 symbolName 优先级更高；为空时回退到 Evidence 原始 symbolName
                    .symbolName(range.symbolName() == null ? evidence.getSymbolName() : range.symbolName())

                    // Range 中的 title 优先级更高；为空时回退到 Evidence 原始 title
                    .title(range.title() == null ? evidence.getTitle() : range.title())

                    .summary(evidence.getSummary())
                    .keywords(evidence.getKeywords())
                    .content(content)
                    .lineStart(start)
                    .lineEnd(end)
                    .lineRange(start + "-" + end)

                    // chunkIndex 用于保留当前 Evidence 内 chunk 的顺序
                    .chunkIndex(index++)

                    // metadata 为空时使用空 Map，避免下游处理出现 NullPointerException
                    .metadata(evidence.getMetadata() == null ? Map.of() : evidence.getMetadata())
                    .build());
        }

        return chunks;
    }

    /**
     * 按固定最大行数生成分块范围。
     *
     * <p>例如 maxLines = 100 时，会生成 1-100、101-200、201-300 这样的范围。
     * 最后一个 Range 的结束行会自动收敛到实际文本行数。</p>
     *
     * @param content  原始文本内容
     * @param maxLines 每个 chunk 最大包含的行数
     * @return 固定行数切分后的 Range 列表
     */
    protected List<Range> fixedRanges(String content, int maxLines) {
        String[] lines = lines(content);
        List<Range> ranges = new ArrayList<>();

        // Range 使用 1-based 行号，便于和代码编辑器/文件行号保持一致
        int start = 1;

        while (start <= lines.length) {
            int end = Math.min(lines.length, start + maxLines - 1);
            ranges.add(new Range(start, end, null, null));
            start = end + 1;
        }

        return ranges;
    }

    /**
     * 将文本内容按平台无关的换行符拆分为行数组。
     *
     * <p>使用 {@code \\R} 可以匹配不同操作系统中的换行符，
     * 例如 {@code \n}、{@code \r\n} 等。</p>
     *
     * @param content 原始文本内容
     * @return 行数组；当内容为空或空白时返回空数组
     */
    protected String[] lines(String content) {
        if (content == null || content.isBlank()) {
            return new String[0];
        }

        // split 的 limit 设置为 -1，用于保留末尾空行
        return content.split("\\R", -1);
    }

    /**
     * 根据起止行号拼接指定范围内的文本。
     *
     * <p>入参 startLine 和 endLine 均为 1-based 行号，
     * 内部访问数组时会转换为 0-based 下标。</p>
     *
     * @param lines     文本行数组
     * @param startLine 起始行号，包含该行
     * @param endLine   结束行号，包含该行
     * @return 拼接后的文本内容
     */
    private String joinLines(String[] lines, int startLine, int endLine) {
        StringBuilder builder = new StringBuilder();

        for (int i = startLine - 1; i < endLine; i++) {
            if (builder.length() > 0) {
                builder.append('\n');
            }
            builder.append(lines[i]);
        }

        return builder.toString();
    }

    /**
     * 表示一个 chunk 对应的原始文本行范围。
     *
     * @param startLine  起始行号，1-based，包含该行
     * @param endLine    结束行号，1-based，包含该行
     * @param title      当前分块的标题，可为空；为空时通常回退到 Evidence 标题
     * @param symbolName 当前分块对应的符号名，可为空；常用于代码类 Evidence
     */
    protected record Range(int startLine, int endLine, String title, String symbolName) {
    }
}