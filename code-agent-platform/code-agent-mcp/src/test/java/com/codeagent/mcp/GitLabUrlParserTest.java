package com.codeagent.mcp;

import com.codeagent.mcp.gitlab.GitLabMergeRequestRef;
import com.codeagent.mcp.gitlab.GitLabUrlParser;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class GitLabUrlParserTest {
    @Test
    void parsesMergeRequestUrl() {
        GitLabMergeRequestRef ref = GitLabUrlParser.parseMergeRequestUrl(
                "https://gitlab.example.com/backend/user-service/-/merge_requests/128");

        assertThat(ref.baseUrl()).isEqualTo("https://gitlab.example.com");
        assertThat(ref.projectPath()).isEqualTo("backend/user-service");
        assertThat(ref.mrIid()).isEqualTo("128");
    }
}
