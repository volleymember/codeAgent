package com.codeagent.llm.client;

import com.codeagent.llm.model.LlmRequest;
import com.codeagent.llm.model.LlmResponse;

public interface LlmClient {
    LlmResponse chat(LlmRequest request);
}
