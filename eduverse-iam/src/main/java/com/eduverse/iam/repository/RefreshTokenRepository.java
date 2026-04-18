package com.eduverse.iam.repository;

import com.eduverse.iam.model.RefreshToken;
import com.eduverse.iam.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByToken(String token);

    @Modifying
    @org.springframework.transaction.annotation.Transactional
    int deleteByUser(User user);
}
