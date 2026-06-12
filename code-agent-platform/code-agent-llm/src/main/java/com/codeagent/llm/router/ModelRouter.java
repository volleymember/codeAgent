package com.codeagent.llm.router;

import com.codeagent.common.enums.ModelTaskType;
import com.codeagent.llm.model.TokenBudget;

public interface ModelRouter {
    String selectModel(ModelTaskType taskType, TokenBudget budget);
}
