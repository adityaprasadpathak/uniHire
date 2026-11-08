package com.unihire.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Global configuration for the Jackson ObjectMapper.
 * Defines custom serialization and deserialization behaviors.
 */
@Configuration
public class JacksonConfig {

    /**
     * Provides the primary {@link ObjectMapper} bean for the application.
     * Registers the JavaTimeModule to handle Java 8 Date/Time API classes properly,
     * and configures it to write dates as ISO-8601 strings rather than numeric timestamps.
     *
     * @return the configured ObjectMapper instance
     */
    @Bean
    @Primary
    public ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper();
        mapper.registerModule(new JavaTimeModule());
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        return mapper;
    }
}
