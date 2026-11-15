package dev.kevin.gallbladder;

import jakarta.persistence.*;
import org.hibernate.annotations.ColumnDefault;
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

    /** Quick-logged with only a severity; cleared once the full form is saved. */
    @ColumnDefault("false")
    @Column(nullable = false)
    private boolean incomplete;

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
        this.incomplete = false;
    }

    /** An episode starting now with only a severity, to be filled in later. */
    static Episode quick(LocalDateTime startedAt, int severity) {
        Episode episode = new Episode(startedAt, null, severity, null, null);
        episode.incomplete = true;
        return episode;
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

    public boolean isIncomplete() {
        return incomplete;
    }
}
