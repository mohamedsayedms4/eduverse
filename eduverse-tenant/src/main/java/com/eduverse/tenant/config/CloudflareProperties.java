package com.eduverse.tenant.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Data
@Configuration
@ConfigurationProperties(prefix = "eduverse.cloudflare")
public class CloudflareProperties {
    private String token;
    private String zoneId;
    private String rootDomain;
    private boolean proxied = true;
}
