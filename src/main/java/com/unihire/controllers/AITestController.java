package com.unihire.controllers;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/ai")
public class AITestController {

    private final ChatClient chatClient;

    public AITestController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    @GetMapping("/test")
    public Map<String, String> testAi(@RequestParam(defaultValue = "Tell me a joke about programming") String prompt) {
        String response = chatClient.prompt()
                .user(prompt)
                .call()
                .content();
                
        return Map.of(
                "prompt", prompt,
                "response", response
        );
    }
}
