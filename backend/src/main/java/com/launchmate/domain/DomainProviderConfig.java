package com.launchmate.domain;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class DomainProviderConfig {

    /** GoDaddy when a token is configured, otherwise a stand-in that reports NOT_CHECKED. */
    @Bean
    DomainProvider domainProvider(@Value("${launchmate.godaddy.base-url}") String baseUrl,
                                  @Value("${launchmate.godaddy.pat:}") String pat,
                                  ObjectMapper json) {
        if (pat == null || pat.isBlank()) {
            return new NoopDomainProvider();
        }
        return new GoDaddyDomainProvider(baseUrl, pat, json);
    }
}
