package com.eduverse.content.model;

import com.eduverse.common.model.BaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Entity
@Table(name = "lessons")
@Getter
@Setter
public class Lesson extends BaseEntity {

    @Column(nullable = false)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private int orderIndex;

    private String youtubeVideoId;

    private String youtubeUrl;

    private Long durationSeconds;

    // Self-hosted video fields
    private String videoPath;          // Path to master.m3u8
    private String videoStatus;        // UPLOADING, PROCESSING, READY, FAILED
    private String videoType;          // YOUTUBE or SELF_HOSTED
    private String originalFileName;

    @Column(nullable = false)
    private String tenantId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "course_id", nullable = false)
    private Course course;
}
