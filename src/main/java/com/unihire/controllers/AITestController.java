package com.unihire.controllers;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST controller for testing AI service integrations.
 * <p>
 * Provides a diagnostic endpoint to verify Spring AI {@link ChatClient} connectivity
 * and test dynamic prompt handling.
 */
@RestController
@RequestMapping("/api/ai")
public class AITestController {

    private final ChatClient chatClient;

    /**
     * Constructs a new {@code AITestController} by initializing a {@link ChatClient}
     * using the provided builder.
     *
     * @param chatClientBuilder the builder used to construct the {@link ChatClient} instance
     */
    public AITestController(ChatClient.Builder chatClientBuilder) {
        this.chatClient = chatClientBuilder.build();
    }

    /**
     * Sends a test prompt to the underlying AI model and returns the prompt and response.
     *
     * @param prompt the text prompt to submit to the AI model (defaults to "Tell me a joke about programming")
     * @return a map containing the original {@code prompt} and the generated AI {@code response}
     */
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
