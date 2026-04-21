package com.eduverse.content.controller;

import com.eduverse.content.config.YouTubeConfig;
import com.eduverse.content.service.youtube.YouTubeCredentialManager;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.googleapis.auth.oauth2.GoogleTokenResponse;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTubeScopes;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Collections;

@RestController
@RequestMapping("/api/admin/youtube")
@RequiredArgsConstructor
public class YouTubeAdminController {

    private final YouTubeConfig youtubeConfig;
    private final YouTubeCredentialManager credentialManager;

    @GetMapping("/oauth-url")
    public String getOAuthUrl() {
        return new GoogleAuthorizationCodeFlow.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                youtubeConfig.getClientId(),
                youtubeConfig.getClientSecret(),
                Collections.singleton(YouTubeScopes.YOUTUBE_UPLOAD)
        )
                .setAccessType("offline")
                .setApprovalPrompt("force")
                .build()
                .newAuthorizationUrl()
                .setRedirectUri(youtubeConfig.getRedirectUri())
                .build();
    }

    @GetMapping("/callback")
    public String callback(@RequestParam("code") String code) throws IOException {
        GoogleTokenResponse response = new GoogleAuthorizationCodeFlow.Builder(
                new NetHttpTransport(),
                GsonFactory.getDefaultInstance(),
                youtubeConfig.getClientId(),
                youtubeConfig.getClientSecret(),
                Collections.singleton(YouTubeScopes.YOUTUBE_UPLOAD)
        )
                .setAccessType("offline")
                .build()
                .newTokenRequest(code)
                .setRedirectUri(youtubeConfig.getRedirectUri())
                .execute();

        credentialManager.saveInitialCredentials(
                response.getAccessToken(),
                response.getRefreshToken(),
                response.getExpiresInSeconds()
        );

        return "Successfully connected to YouTube! You can now close this window.";
    }
}
