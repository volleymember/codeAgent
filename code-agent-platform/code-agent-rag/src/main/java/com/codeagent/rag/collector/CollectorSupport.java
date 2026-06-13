package com.codeagent.rag.collector;

import com.codeagent.common.exception.BusinessException;
import com.codeagent.rag.model.IndexEvidenceRequest;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 证据采集器公共支持类。
 *
 * <p>该类封装了不同采集器中通用的辅助逻辑，例如：</p>
 * <ul>
 *     <li>生成 Evidence 唯一标识</li>
 *     <li>校验必填文本字段</li>
 *     <li>构建统一的 metadata 元数据</li>
 *     <li>处理字段默认值</li>
 * </ul>
 *
 * <p>具体的采集器实现类可以继承该类，复用这些基础能力，
 * 从而减少重复代码并保持 Evidence 构建逻辑的一致性。</p>
 */
abstract class CollectorSupport {

    /**
     * 生成新的 Evidence ID。
     *
     * <p>ID 格式为：</p>
     * <pre>
     * EVID-yyyyMMddHHmmss-xxxxxxxx
     * </pre>
     *
     * <p>其中：</p>
     * <ul>
     *     <li>{@code yyyyMMddHHmmss} 表示当前时间，精确到秒</li>
     *     <li>{@code xxxxxxxx} 表示 UUID 的前 8 位，用于降低并发场景下的重复概率</li>
     * </ul>
     *
     * @return 新生成的 Evidence ID
     */
    protected String nextEvidenceId() {
        return "EVID-" + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + UUID.randomUUID().toString().substring(0, 8);
    }

    /**
     * 校验文本字段是否非空。
     *
     * <p>当字段为 null、空字符串或仅包含空白字符时，抛出业务异常。</p>
     *
     * @param value   待校验的文本值
     * @param code    业务异常错误码
     * @param message 业务异常提示信息
     * @throws BusinessException 当 value 为空或空白时抛出
     */
    protected void requireText(String value, String code, String message) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(code, message);
        }
    }

    /**
     * 根据索引请求构建 Evidence 元数据。
     *
     * <p>该方法会先复制请求中已有的 metadata，
     * 然后补充常用上下文字段，例如 branch、commitId、buildId 和 rawRef。</p>
     *
     * <p>补充字段时会自动跳过 null 或空白值，避免 metadata 中出现无意义数据。</p>
     *
     * @param request Evidence 索引请求
     * @return 构建完成的 metadata，保持插入顺序
     */
    protected Map<String, Object> metadata(IndexEvidenceRequest request) {
        Map<String, Object> metadata = new LinkedHashMap<>();

        // 保留请求方传入的自定义 metadata
        if (request.getMetadata() != null) {
            metadata.putAll(request.getMetadata());
        }

        // 补充常用的 Evidence 上下文字段
        put(metadata, "branch", request.getBranch());
        put(metadata, "commitId", request.getCommitId());
        put(metadata, "buildId", request.getBuildId());
        put(metadata, "rawRef", request.getRawRef());

        return metadata;
    }

    /**
     * 向 metadata 中写入非空值。
     *
     * <p>只有当 value 不为 null，且转换为字符串后不是空白内容时，才会写入 Map。</p>
     *
     * @param metadata 目标元数据 Map
     * @param key      元数据字段名
     * @param value    元数据字段值
     */
    protected void put(Map<String, Object> metadata, String key, Object value) {
        if (value != null && !String.valueOf(value).isBlank()) {
            metadata.put(key, value);
        }
    }

    /**
     * 返回文本值或默认值。
     *
     * <p>当 value 为 null、空字符串或仅包含空白字符时，返回 fallback；
     * 否则返回 value 本身。</p>
     *
     * @param value    原始文本值
     * @param fallback 默认文本值
     * @return 非空的原始值或默认值
     */
    protected String fallback(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}