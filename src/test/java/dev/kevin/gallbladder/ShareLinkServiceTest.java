package dev.kevin.gallbladder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ShareLinkServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 15, 12, 0);

    @Autowired ShareLinkRepository links;
    MutableClock clock = new MutableClock(NOW);
    ShareLinkService service;

    @BeforeEach
    void setUp() {
        service = new ShareLinkService(links, clock);
    }

    @Test
    void newLinkOpensUntilItExpires() {
        ShareLinkService.Created created = service.create("  Dr. Wang ", 7);

        assertThat(created.link().getLabel()).isEqualTo("Dr. Wang");
        assertThat(created.link().getExpiresAt()).isEqualTo(NOW.plusDays(7));
        assertThat(service.findActive(created.token())).isPresent();

        clock.now = NOW.plusDays(7).minusMinutes(1);
        assertThat(service.findActive(created.token())).isPresent();
        clock.now = NOW.plusDays(7);
        assertThat(service.findActive(created.token())).as("expired").isEmpty();
        assertThat(service.active()).isEmpty();
    }

    @Test
    void revokedLinkStopsWorking() {
        ShareLinkService.Created created = service.create(null, 30);

        service.revoke(created.link().getId());

        assertThat(service.findActive(created.token())).isEmpty();
        assertThat(service.active()).isEmpty();
    }

    @Test
    void onlyTheHashIsStoredAndTokensAreLongAndUnique() {
        ShareLinkService.Created a = service.create(null, 30);
        ShareLinkService.Created b = service.create(null, 30);

        assertThat(a.token()).hasSizeGreaterThanOrEqualTo(43).isNotEqualTo(b.token());
        ShareLink saved = links.findById(a.link().getId()).orElseThrow();
        assertThat(links.findByTokenHash(a.token())).as("token itself not stored").isEmpty();
        assertThat(links.findByTokenHash(ShareLinkService.hash(a.token()))).contains(saved);
    }

    @Test
    void unknownOrOddTokensFindNothing() {
        service.create(null, 30);

        assertThat(service.findActive("not-a-real-token")).isEmpty();
        assertThat(service.findActive("x".repeat(500))).isEmpty();
        assertThat(service.findActive(null)).isEmpty();
    }

    @Test
    void oddLifetimesFallBackToTheDefault() {
        assertThat(service.create(null, 3650).link().getExpiresAt()).isEqualTo(NOW.plusDays(30));
    }

    /** A clock the test can move forward. */
    static class MutableClock extends Clock {
        LocalDateTime now;

        MutableClock(LocalDateTime now) { this.now = now; }

        @Override public ZoneId getZone() { return ZoneId.of("UTC"); }
        @Override public Clock withZone(ZoneId zone) { return this; }
        @Override public Instant instant() { return now.toInstant(ZoneOffset.UTC); }
    }
}
