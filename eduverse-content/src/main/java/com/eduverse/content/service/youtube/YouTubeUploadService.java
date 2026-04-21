package com.eduverse.content.service.youtube;

import com.eduverse.content.config.YouTubeConfig;
import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.googleapis.javanet.GoogleNetHttpTransport;
import com.google.api.client.googleapis.media.MediaHttpUploader;
import com.google.api.client.googleapis.media.MediaHttpUploaderProgressListener;
import com.google.api.client.http.InputStreamContent;
import com.google.api.client.json.gson.GsonFactory;
import com.google.api.services.youtube.YouTube;
import com.google.api.services.youtube.model.Video;
import com.google.api.services.youtube.model.VideoSnippet;
import com.google.api.services.youtube.model.VideoStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.Collections;

@Service
@Slf4j
@RequiredArgsConstructor
public class YouTubeUploadService {

    private final YouTubeCredentialManager credentialManager;
    private final YouTubeConfig youtubeConfig;

    public String uploadVideo(MultipartFile file, String title, String description) throws IOException, GeneralSecurityException {
        Credential credential = credentialManager.getCredential();

        YouTube youtubeService = new YouTube.Builder(
                GoogleNetHttpTransport.newTrustedTransport(),
                GsonFactory.getDefaultInstance(),
                credential)
                .setApplicationName(youtubeConfig.getApplicationName())
                .build();

        Video videoObjectDefiningMetadata = new Video();

        VideoStatus status = new VideoStatus();
        status.setPrivacyStatus("unlisted"); // Always unlisted as per requirement
        videoObjectDefiningMetadata.setStatus(status);

        VideoSnippet snippet = new VideoSnippet();
        snippet.setTitle(title);
        snippet.setDescription(description);
        snippet.setTags(Collections.singletonList("Eduverse"));
        videoObjectDefiningMetadata.setSnippet(snippet);

        InputStreamContent mediaContent = new InputStreamContent(
                file.getContentType(), file.getInputStream());

        YouTube.Videos.Insert videoInsert = youtubeService.videos()
                .insert(Collections.singletonList("snippet,statistics,status"), videoObjectDefiningMetadata, mediaContent);

        MediaHttpUploader uploader = videoInsert.getMediaHttpUploader();
        uploader.setDirectUploadEnabled(false); // Enable Resumable Upload
        uploader.setChunkSize(MediaHttpUploader.MINIMUM_CHUNK_SIZE * 4); // 1MB chunks

        uploader.setProgressListener(new MediaHttpUploaderProgressListener() {
            @Override
            public void progressChanged(MediaHttpUploader uploader) throws IOException {
                switch (uploader.getUploadState()) {
                    case INITIATION_STARTED:
                        log.info("Initiation Started");
                        break;
                    case INITIATION_COMPLETE:
                        log.info("Initiation Completed");
                        break;
                    case MEDIA_IN_PROGRESS:
                        log.debug("Upload in progress: " + (uploader.getProgress() * 100) + "%");
                        break;
                    case MEDIA_COMPLETE:
                        log.info("Upload Completed!");
                        break;
                }
            }
        });

        Video returnedVideo = videoInsert.execute();
        log.info("Video uploaded with ID: " + returnedVideo.getId());

        return returnedVideo.getId();
    }
}
