package dev.kevin.gallbladder;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDateTime;

/** What the episode form submits. */
public class EpisodeForm {

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime startedAt;
    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime endedAt;
    private Integer severity;
    private String location;
    private String note;

    /** A form filled in from a saved episode, for editing. */
    static EpisodeForm from(Episode episode) {
        EpisodeForm form = new EpisodeForm();
        form.startedAt = episode.getStartedAt();
        form.endedAt = episode.getEndedAt();
        form.severity = episode.getSeverity();
        form.location = episode.getLocation();
        form.note = episode.getNote();
        return form;
    }

    public LocalDateTime getStartedAt() { return startedAt; }
    public void setStartedAt(LocalDateTime startedAt) { this.startedAt = startedAt; }
    public LocalDateTime getEndedAt() { return endedAt; }
    public void setEndedAt(LocalDateTime endedAt) { this.endedAt = endedAt; }
    public Integer getSeverity() { return severity; }
    public void setSeverity(Integer severity) { this.severity = severity; }
    public String getLocation() { return location; }
    public void setLocation(String location) { this.location = location; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
}
