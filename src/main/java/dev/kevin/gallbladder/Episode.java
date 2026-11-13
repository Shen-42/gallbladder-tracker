package dev.kevin.gallbladder;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

import java.time.LocalDateTime;

@Entity
@SQLRestriction("deleted_at is null") // deleted episodes are hidden from every query until restored
public class Episode {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime startedAt;

    private LocalDateTime endedAt;

    /** 0 (no pain) to 10 (worst pain imaginable). */
    @Column(nullable = false)
    private int severity;

    private String location;

    private String note;

    private LocalDateTime deletedAt;

    protected Episode() {
    }

    public Episode(LocalDateTime startedAt, LocalDateTime endedAt, int severity, String location, String note) {
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.severity = severity;
        this.location = location;
        this.note = note;
    }

    public void update(LocalDateTime startedAt, LocalDateTime endedAt, int severity, String location, String note) {
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.severity = severity;
        this.location = location;
        this.note = note;
    }

    public void markDeleted(LocalDateTime when) {
        this.deletedAt = when;
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public LocalDateTime getEndedAt() {
        return endedAt;
    }

    public int getSeverity() {
        return severity;
    }

    public String getLocation() {
        return location;
    }

    public String getNote() {
        return note;
    }
}
