package com.codeagent.rag.chunk;

import com.codeagent.rag.config.RagProperties;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 简单文本分块器。
 *
 * <p>该分块器按照配置中的字符长度对文本进行切分，并支持相邻 chunk 之间保留一定重叠内容。
 * 适用于不需要解析结构、只需要快速按长度拆分的普通文档场景。</p>
 *
 * <p>与基于行号或语义结构的分块器不同，该类直接按字符偏移量切分内容，
 * 因此可以更精确地控制每个 chunk 的最大字符数。</p>
 */
@Component
public class SimpleChunker {

    /**
     * RAG 相关配置。
     *
     * <p>用于读取 chunkSize 和 chunkOverlap 等分块参数。</p>
     */
    private final RagProperties properties;

    /**
     * 创建简单文本分块器。
     *
     * @param properties RAG 配置对象，提供分块大小和重叠长度
     */
    public SimpleChunker(RagProperties properties) {
        this.properties = properties;
    }

    /**
     * 将文本内容按固定字符长度拆分为多个 DocumentChunk。
     *
     * <p>分块规则如下：</p>
     * <ul>
     *     <li>如果内容为空或全为空白，返回空列表</li>
     *     <li>chunkSize 最小值为 100，避免配置过小导致生成过多碎片</li>
     *     <li>chunkOverlap 最小为 0，最大不能超过 chunkSize - 1</li>
     *     <li>每个 chunk 会记录其在原文中的起止行号和顺序索引</li>
     * </ul>
     *
     * @param title   文档标题
     * @param content 原始文档内容
     * @return 拆分后的 DocumentChunk 列表
     */
    public List<DocumentChunk> chunk(String title, String content) {
        List<DocumentChunk> chunks = new ArrayList<>();

        // 空内容不生成任何 chunk
        if (content == null || content.isBlank()) {
            return chunks;
        }

        // chunkSize 至少为 100，避免分块过小影响检索质量和性能
        int size = Math.max(100, properties.getChunkSize());

        // overlap 限制在 [0, size - 1]，避免出现无限循环或无效重叠
        int overlap = Math.min(Math.max(0, properties.getChunkOverlap()), size - 1);

        int start = 0;
        int index = 0;

        while (start < content.length()) {
            // 当前 chunk 的结束偏移量，不能超过文本总长度
            int end = Math.min(content.length(), start + size);

            // 根据字符偏移量计算当前 chunk 在原文中的行号范围
            int lineStart = lineNumber(content, start);
            int lineEnd = lineNumber(content, Math.max(start, end - 1));

            chunks.add(new DocumentChunk(
                    title,
                    content.substring(start, end),
                    lineStart,
                    lineEnd,
                    index++
            ));

            // 已经到达文本末尾，分块结束
            if (end == content.length()) {
                break;
            }

            // 下一个 chunk 从当前结束位置回退 overlap 个字符开始，实现重叠窗口
            start = end - overlap;
        }

        return chunks;
    }

    /**
     * 根据字符偏移量计算其所在的行号。
     *
     * <p>行号从 1 开始。方法通过统计 offset 之前出现的换行符数量来计算行号。</p>
     *
     * @param content 原始文本内容
     * @param offset  字符偏移量，0-based
     * @return offset 所在的行号，1-based
     */
    private int lineNumber(String content, int offset) {
        int line = 1;

        for (int i = 0; i < offset && i < content.length(); i++) {
            if (content.charAt(i) == '\n') {
                line++;
            }
        }

        return line;
    }
}