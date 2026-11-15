package dev.kevin.gallbladder;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface ShareLinkRepository extends JpaRepository<ShareLink, Long> {

    Optional<ShareLink> findByTokenHash(String tokenHash);

    List<ShareLink> findByRevokedAtIsNullAndExpiresAtAfterOrderByExpiresAt(LocalDateTime now);
}
