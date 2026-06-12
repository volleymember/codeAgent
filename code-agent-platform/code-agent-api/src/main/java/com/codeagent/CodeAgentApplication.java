package com.codeagent;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication(scanBasePackages = "com.codeagent")
public class CodeAgentApplication {
    public static void main(String[] args) {
        SpringApplication.run(CodeAgentApplication.class, args);
    }
}
