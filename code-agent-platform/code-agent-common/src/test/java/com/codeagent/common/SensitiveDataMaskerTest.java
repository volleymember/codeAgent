package com.codeagent.common;

import com.codeagent.common.security.SensitiveDataMasker;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveDataMaskerTest {
    @Test
    void masksCommonSecrets() {
        String masked = SensitiveDataMasker.mask("Authorization: Bearer abc.def token=secret password=123456");

        assertThat(masked).doesNotContain("abc.def", "secret", "123456");
        assertThat(masked).contains("***MASKED***");
    }
}
