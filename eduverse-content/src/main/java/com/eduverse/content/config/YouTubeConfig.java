package com.eduverse.content.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "eduverse.youtube")
@Data
public class YouTubeConfig {
    private String clientId;
    private String clientSecret;
    private String redirectUri;
    private String applicationName = "Eduverse Platform";
}
