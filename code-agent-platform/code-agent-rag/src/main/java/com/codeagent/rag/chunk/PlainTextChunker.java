package com.codeagent.rag.chunk;

import com.codeagent.rag.model.Evidence;
import com.codeagent.rag.model.EvidenceChunk;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 普通文本兜底分块器。
 *
 * <p>该分块器不依赖具体文件类型或内容结构，而是直接按照固定行数进行分块。
 * 它通常作为最后的兜底分块器使用，保证即使前面的专用分块器都不支持某个 Evidence，
 * 也仍然可以将内容拆分为可索引、可检索的 EvidenceChunk。</p>
 *
 * <p>由于 {@link #supports(Evidence)} 始终返回 true，因此该分块器的执行顺序
 * 应当排在更具体的分块器之后。</p>
 */
@Order
@Component
public class PlainTextChunker extends ChunkSupport implements EvidenceChunker {

    /**
     * 每个普通文本 chunk 最多包含的行数。
     */
    private static final int MAX_LINES = 80;

    /**
     * 判断当前分块器是否支持指定 Evidence。
     *
     * <p>普通文本分块器作为兜底实现，默认支持所有 Evidence。</p>
     *
     * @param evidence 待判断的 Evidence
     * @return 始终返回 true
     */
    @Override
    public boolean supports(Evidence evidence) {
        return true;
    }

    /**
     * 将 Evidence 按固定行数拆分为多个 EvidenceChunk。
     *
     * <p>该方法不解析文本结构，只调用 {@link ChunkSupport#fixedRanges(String, int)}
     * 生成固定行数范围，然后通过 {@link ChunkSupport#buildChunks(Evidence, List)}
     * 构建最终的 chunk 列表。</p>
     *
     * @param evidence 原始 Evidence
     * @return 分块后的 EvidenceChunk 列表
     */
    @Override
    public List<EvidenceChunk> chunk(Evidence evidence) {
        return buildChunks(evidence, fixedRanges(evidence.getContent(), MAX_LINES));
    }
}