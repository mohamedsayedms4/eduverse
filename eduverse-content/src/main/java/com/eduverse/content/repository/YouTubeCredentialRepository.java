package com.eduverse.content.repository;

import com.eduverse.content.model.YouTubeChannelCredential;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface YouTubeCredentialRepository extends JpaRepository<YouTubeChannelCredential, Long> {
}
