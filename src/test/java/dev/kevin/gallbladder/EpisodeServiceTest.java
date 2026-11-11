package dev.kevin.gallbladder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class EpisodeServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 11, 12, 0);

    @Autowired EpisodeRepository episodes;
    EpisodeService service;

    @BeforeEach
    void setUp() {
        service = new EpisodeService(episodes, Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneId.of("UTC")));
    }

    @Test
    void savesWithOnlyStartAndSeverity() {
        EpisodeForm form = form(NOW.minusHours(2), null, 6);

        Episode episode = service.log(form, errors(form)).orElseThrow();

        assertThat(episode.getStartedAt()).isEqualTo(NOW.minusHours(2));
        assertThat(episode.getSeverity()).isEqualTo(6);
        assertThat(episode.getEndedAt()).isNull();
        assertThat(episode.getLocation()).isNull();
        assertThat(episode.getNote()).isNull();
    }

    @Test
    void savesOptionalFields() {
        EpisodeForm form = form(NOW.minusHours(2), NOW.minusHours(1), 0);
        form.setLocation("  upper right abdomen ");
        form.setNote("after dinner");

        Episode episode = service.log(form, errors(form)).orElseThrow();

        assertThat(episode.getEndedAt()).isEqualTo(NOW.minusHours(1));
        assertThat(episode.getSeverity()).isZero();
        assertThat(episode.getLocation()).isEqualTo("upper right abdomen");
        assertThat(episode.getNote()).isEqualTo("after dinner");
    }

    @Test
    void endBeforeStartIsRejected() {
        EpisodeForm form = form(NOW.minusHours(1), NOW.minusHours(2), 5);
        Errors errors = errors(form);

        assertThat(service.log(form, errors)).isEmpty();
        assertThat(errors.getFieldError("endedAt").getCode()).isEqualTo("beforeStart");
        assertThat(episodes.count()).isZero();
    }

    @Test
    void futureStartIsRejected() {
        EpisodeForm form = form(NOW.plusMinutes(1), null, 5);
        Errors errors = errors(form);

        assertThat(service.log(form, errors)).isEmpty();
        assertThat(errors.getFieldError("startedAt")).isNotNull();
    }

    @Test
    void missingStartIsRejected() {
        EpisodeForm form = form(null, null, 5);
        Errors errors = errors(form);

        assertThat(service.log(form, errors)).isEmpty();
        assertThat(errors.getFieldError("startedAt")).isNotNull();
    }

    @Test
    void severityMustBeGivenAndWithin0To10() {
        for (Integer severity : new Integer[]{null, -1, 11}) {
            EpisodeForm form = form(NOW, null, severity);
            Errors errors = errors(form);

            assertThat(service.log(form, errors)).isEmpty();
            assertThat(errors.getFieldError("severity")).isNotNull();
        }
    }

    private static EpisodeForm form(LocalDateTime start, LocalDateTime end, Integer severity) {
        EpisodeForm form = new EpisodeForm();
        form.setStartedAt(start);
        form.setEndedAt(end);
        form.setSeverity(severity);
        return form;
    }

    private static Errors errors(EpisodeForm form) {
        return new BeanPropertyBindingResult(form, "episodeForm");
    }
}
