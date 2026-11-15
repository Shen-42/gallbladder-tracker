package dev.kevin.gallbladder;

import jakarta.persistence.*;

import java.time.LocalDateTime;

/** A read-only link to the record, for a doctor. Only a hash of its secret token is stored. */
@Entity
public class ShareLink {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** SHA-256 of the token, in hex. */
    @Column(nullable = false, unique = true, length = 64)
    private String tokenHash;

    private String label;

    @Column(nullable = false)
    private LocalDateTime createdAt;

    @Column(nullable = false)
    private LocalDateTime expiresAt;

    private LocalDateTime revokedAt;

    protected ShareLink() {
    }

    ShareLink(String tokenHash, String label, LocalDateTime createdAt, LocalDateTime expiresAt) {
        this.tokenHash = tokenHash;
        this.label = label;
        this.createdAt = createdAt;
        this.expiresAt = expiresAt;
    }

    boolean isActive(LocalDateTime now) {
        return revokedAt == null && now.isBefore(expiresAt);
    }

    void revoke(LocalDateTime when) {
        this.revokedAt = when;
    }

    public Long getId() {
        return id;
    }

    public String getLabel() {
        return label;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }
}
