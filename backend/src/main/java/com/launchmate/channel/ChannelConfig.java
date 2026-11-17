package com.launchmate.channel;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class ChannelConfig {

    @Bean
    YouTubeClient youTubeClient(@Value("${launchmate.youtube.base-url}") String baseUrl,
                                @Value("${launchmate.youtube.api-key:}") String apiKey, ObjectMapper json) {
        return new YouTubeClient(baseUrl, apiKey, json);
    }
}
