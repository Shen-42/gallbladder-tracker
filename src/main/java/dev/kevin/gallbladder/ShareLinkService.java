package dev.kevin.gallbladder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class ShareLinkService {

    /** How long a new link can last, in days. */
    static final Set<Integer> ALLOWED_DAYS = Set.of(7, 30, 90);
    static final int DEFAULT_DAYS = 30;

    /** The saved link and its token. The token is only available here; the database keeps its hash. */
    public record Created(ShareLink link, String token) {
    }

    private final ShareLinkRepository links;
    private final Clock clock;
    private final SecureRandom random = new SecureRandom();

    public ShareLinkService(ShareLinkRepository links, Clock clock) {
        this.links = links;
        this.clock = clock;
    }

    /** A new link lasting {@code days} (one of {@link #ALLOWED_DAYS}, otherwise {@value #DEFAULT_DAYS}). */
    @Transactional
    public Created create(String label, int days) {
        byte[] bytes = new byte[32];
        random.nextBytes(bytes);
        String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        LocalDateTime now = LocalDateTime.now(clock).truncatedTo(ChronoUnit.MINUTES);
        int lifetime = ALLOWED_DAYS.contains(days) ? days : DEFAULT_DAYS;
        String cleanLabel = label == null || label.isBlank() ? null : label.trim();
        ShareLink link = links.save(new ShareLink(hash(token), cleanLabel, now, now.plusDays(lifetime)));
        return new Created(link, token);
    }

    /** The link for {@code token}, if it exists, hasn't expired and hasn't been revoked. */
    @Transactional(readOnly = true)
    public Optional<ShareLink> findActive(String token) {
        if (token == null || token.length() > 100) {
            return Optional.empty();
        }
        LocalDateTime now = LocalDateTime.now(clock);
        return links.findByTokenHash(hash(token)).filter(link -> link.isActive(now));
    }

    @Transactional(readOnly = true)
    public List<ShareLink> active() {
        return links.findByRevokedAtIsNullAndExpiresAtAfterOrderByExpiresAt(LocalDateTime.now(clock));
    }

    @Transactional
    public void revoke(long id) {
        links.findById(id).orElseThrow(NotFoundException::new).revoke(LocalDateTime.now(clock));
    }

    static String hash(String token) {
        try {
            return HexFormat.of().formatHex(
                    MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
