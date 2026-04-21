package com.eduverse.content.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.LocalDateTime;

@Entity
@Table(name = "youtube_channel_credentials", schema = "dbo")
@Getter
@Setter
public class YouTubeChannelCredential {

    @Id
    private Long id = 1L; // Only one record for the platform

    private String channelId;

    @Column(length = 2000)
    private String accessToken;

    @Column(length = 2000)
    private String refreshToken;

    private LocalDateTime expiresAt;

    private LocalDateTime createdAt = LocalDateTime.now();
    
    private LocalDateTime updatedAt = LocalDateTime.now();
}
