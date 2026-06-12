package com.codeagent.llm.router;

import com.codeagent.common.enums.ModelTaskType;
import com.codeagent.llm.config.LlmProperties;
import com.codeagent.llm.model.TokenBudget;
import org.springframework.stereotype.Component;

@Component
public class FixedDeepSeekModelRouter implements ModelRouter {
    private final LlmProperties properties;

    public FixedDeepSeekModelRouter(LlmProperties properties) {
        this.properties = properties;
    }

    @Override
    public String selectModel(ModelTaskType taskType, TokenBudget budget) {
        return properties.getDefaultModel();
    }
}
