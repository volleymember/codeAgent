package com.codeagent.storage.raw;

public interface RawOutputStore {
    String saveJson(String taskNo, String objectName, Object payload);
}
