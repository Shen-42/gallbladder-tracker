package dev.kevin.gallbladder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.Errors;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Optional;

@Service
public class EpisodeService {

    private static final String FUTURE = "That time is in the future. Pick now or earlier.";

    private final EpisodeRepository episodes;
    private final Clock clock;

    public EpisodeService(EpisodeRepository episodes, Clock clock) {
        this.episodes = episodes;
        this.clock = clock;
    }

    /**
     * Checks the form, recording problems in {@code errors} next to the field they belong to.
     * Saves nothing unless the whole form is valid.
     */
    @Transactional
    public Optional<Episode> log(EpisodeForm form, Errors errors) {
        if (!isValid(form, errors)) {
            return Optional.empty();
        }
        return Optional.of(episodes.save(new Episode(form.getStartedAt(), form.getEndedAt(), form.getSeverity(),
                blankToNull(form.getLocation()), blankToNull(form.getNote()))));
    }

    /** Like {@link #log}, but changes an existing episode. */
    @Transactional
    public Optional<Episode> update(long id, EpisodeForm form, Errors errors) {
        Episode episode = episodes.findById(id).orElseThrow(NotFoundException::new);
        if (!isValid(form, errors)) {
            return Optional.empty();
        }
        episode.update(form.getStartedAt(), form.getEndedAt(), form.getSeverity(),
                blankToNull(form.getLocation()), blankToNull(form.getNote()));
        return Optional.of(episode);
    }

    /** Hides the episode everywhere until {@link #restore} is called. Returns it so the caller can say what was deleted. */
    @Transactional
    public Episode delete(long id) {
        Episode episode = episodes.findById(id).orElseThrow(NotFoundException::new);
        episode.markDeleted(LocalDateTime.now(clock));
        return episode;
    }

    @Transactional
    public Episode restore(long id) {
        if (episodes.restore(id) == 0) {
            throw new NotFoundException();
        }
        return episodes.findById(id).orElseThrow(NotFoundException::new);
    }

    private boolean isValid(EpisodeForm form, Errors errors) {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime start = form.getStartedAt();
        LocalDateTime end = form.getEndedAt();

        if (start == null) {
            errors.rejectValue("startedAt", "required", "Enter when the pain started.");
        } else if (start.isAfter(now)) {
            errors.rejectValue("startedAt", "future", FUTURE);
        }
        if (end != null) {
            if (start != null && end.isBefore(start)) {
                errors.rejectValue("endedAt", "beforeStart",
                        "The end is before the start. Pick a time after it started, or leave it empty.");
            } else if (end.isAfter(now)) {
                errors.rejectValue("endedAt", "future", FUTURE + " Leave it empty if the pain hasn't stopped.");
            }
        }
        Integer severity = form.getSeverity();
        if (severity == null || severity < 0 || severity > 10) {
            errors.rejectValue("severity", "required", "Choose how bad the pain is, from 0 to 10.");
        }
        return !errors.hasErrors();
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }
}
