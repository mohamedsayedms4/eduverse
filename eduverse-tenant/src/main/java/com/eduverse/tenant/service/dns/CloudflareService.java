package com.eduverse.tenant.service.dns;

import com.eduverse.tenant.config.CloudflareProperties;
import lombok.Builder;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Slf4j
@Service
@RequiredArgsConstructor
public class CloudflareService {

    private final RestTemplate restTemplate;
    private final CloudflareProperties properties;
    
    private static final String CLOUDFLARE_API_URL = "https://api.cloudflare.com/client/v4/zones/{zoneId}/dns_records";

    public void createSubdomain(String subdomain) {
        if (properties.getToken() == null || properties.getToken().contains("your_api_token")) {
            log.warn("Cloudflare token not configured. Skipping DNS record creation for: {}", subdomain);
            return;
        }

        // Use UriComponentsBuilder to safely construct the URI without accidental template expansion
        java.net.URI uri = org.springframework.web.util.UriComponentsBuilder
                .fromHttpUrl(CLOUDFLARE_API_URL)
                .buildAndExpand(properties.getZoneId())
                .toUri();
        
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setBearerAuth(properties.getToken());

        CloudflareDnsRequest requestBody = CloudflareDnsRequest.builder()
                .type("CNAME")
                .name(subdomain) 
                .content(properties.getRootDomain())
                .proxied(properties.isProxied())
                .ttl(1)
                .build();

        HttpEntity<CloudflareDnsRequest> request = new HttpEntity<>(requestBody, headers);

        try {
            log.info("Requesting Cloudflare to create CNAME: {} -> {}", subdomain, properties.getRootDomain());
            String response = restTemplate.postForObject(uri, request, String.class);
            log.info("Cloudflare Response: {}", response);
        } catch (Exception e) {
            log.error("Failed to create DNS record for subdomain: {}", subdomain, e);
            // We log the error but don't throw it to avoid rolling back the tenant registration
            // unless the user specifies otherwise.
        }
    }

    @Data
    @Builder
    private static class CloudflareDnsRequest {
        private String type;
        private String name;
        private String content;
        private boolean proxied;
        private int ttl;
    }
}
