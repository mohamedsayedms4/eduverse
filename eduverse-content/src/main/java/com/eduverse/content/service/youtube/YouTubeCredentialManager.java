package com.eduverse.content.service.youtube;

import com.eduverse.content.config.YouTubeConfig;
import com.eduverse.content.model.YouTubeChannelCredential;
import com.eduverse.content.repository.YouTubeCredentialRepository;
import com.google.api.client.auth.oauth2.BearerToken;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.auth.oauth2.TokenResponse;
import com.google.api.client.googleapis.auth.oauth2.GoogleRefreshTokenRequest;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.time.LocalDateTime;

@Service
@Slf4j
@RequiredArgsConstructor
public class YouTubeCredentialManager {

    private final YouTubeCredentialRepository credentialRepository;
    private final YouTubeConfig youtubeConfig;

    public Credential getCredential() throws IOException {
        YouTubeChannelCredential record = credentialRepository.findById(1L)
                .orElseThrow(() -> new RuntimeException("YouTube credentials not configured. Please complete OAuth flow."));

        if (record.getExpiresAt().isBefore(LocalDateTime.now().plusMinutes(5))) {
            refreshAccessToken(record);
        }

        return new Credential(BearerToken.authorizationHeaderAccessMethod())
                .setAccessToken(record.getAccessToken());
    }

    private void refreshAccessToken(YouTubeChannelCredential record) throws IOException {
        log.info("Refreshing YouTube access token...");
        
        TokenResponse response = new GoogleRefreshTokenRequest(
                new NetHttpTransport(),
                new GsonFactory(),
                record.getRefreshToken(),
                youtubeConfig.getClientId(),
                youtubeConfig.getClientSecret()
        ).execute();

        record.setAccessToken(response.getAccessToken());
        record.setExpiresAt(LocalDateTime.now().plusSeconds(response.getExpiresInSeconds()));
        record.setUpdatedAt(LocalDateTime.now());
        
        credentialRepository.save(record);
        log.info("YouTube access token refreshed successfully.");
    }
    
    public void saveInitialCredentials(String accessToken, String refreshToken, Long expiresInSeconds) {
        YouTubeChannelCredential record = credentialRepository.findById(1L)
                .orElse(new YouTubeChannelCredential());
        
        record.setAccessToken(accessToken);
        record.setRefreshToken(refreshToken);
        record.setExpiresAt(LocalDateTime.now().plusSeconds(expiresInSeconds));
        record.setUpdatedAt(LocalDateTime.now());
        
        credentialRepository.save(record);
    }
}
