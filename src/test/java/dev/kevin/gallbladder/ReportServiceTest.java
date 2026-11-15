package dev.kevin.gallbladder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.openpdf.text.pdf.PdfReader;
import org.openpdf.text.pdf.parser.PdfTextExtractor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;

import java.io.IOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class ReportServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 15, 12, 0);

    @Autowired MealRepository meals;
    @Autowired EpisodeRepository episodes;
    @Autowired SettingsRepository settings;
    @Autowired FoodRepository foods;
    ReportService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneId.of("UTC"));
        service = new ReportService(new SummaryService(meals, episodes, settings, clock),
                new ChartService(meals, episodes, settings, clock), episodes, clock);
    }

    @Test
    void reportWritesOutDatesUnitsAndEachEpisode() throws IOException {
        Settings s = new Settings();
        s.setFatThresholdGrams(new BigDecimal("15"));
        settings.save(s);
        Food oil = foods.save(new Food("Olive oil", new BigDecimal("100"), FoodSource.CUSTOM));
        Meal meal = new Meal(LocalDateTime.of(2026, 11, 10, 12, 0), null);
        meal.addItem(oil, new BigDecimal("20"));
        meals.save(meal);
        episodes.save(new Episode(LocalDateTime.of(2026, 11, 10, 14, 30), LocalDateTime.of(2026, 11, 10, 16, 0),
                7, "upper right abdomen", "after lunch"));
        episodes.save(Episode.quick(LocalDateTime.of(2026, 11, 12, 9, 0), 4));

        String text = text(service.pdf(LocalDate.of(2026, 11, 1), LocalDate.of(2026, 11, 15)));

        assertThat(text).contains("1 November 2026 to 15 November 2026 (15 days)")
                .contains("Fat threshold per meal: 15 grams")
                .contains("Over 15 g fat")
                .contains("Median interval from meal to pain onset: 2 hours 30 minutes")
                .contains("Pain episodes (2)")
                .contains("Tue 10 Nov 2026", "14:30", "16:00 (1 hour 30 minutes)", "7 / 10", "upper right abdomen")
                .contains("Logged quickly; details not filled in.")
                .contains("not a diagnosis");
    }

    @Test
    void emptyPeriodStillMakesAReport() throws IOException {
        String text = text(service.pdf(LocalDate.of(2026, 11, 15), LocalDate.of(2026, 11, 15)));

        assertThat(text).contains("(1 day)").contains("No fat threshold per meal has been set")
                .contains("No episodes were recorded in this period.");
    }

    @Test
    void durationsAreWrittenOut() {
        assertThat(ReportService.duration(Duration.ofMinutes(45))).isEqualTo("45 minutes");
        assertThat(ReportService.duration(Duration.ofMinutes(60))).isEqualTo("1 hour");
        assertThat(ReportService.duration(Duration.ofMinutes(121))).isEqualTo("2 hours 1 minute");
    }

    @Test
    void axisStepsAreRound() {
        assertThat(ReportService.niceStep(3.3)).isEqualTo(5);
        assertThat(ReportService.niceStep(11)).isEqualTo(20);
        assertThat(ReportService.niceStep(0.3)).isEqualTo(0.5);
    }

    private static String text(byte[] pdf) throws IOException {
        PdfReader reader = new PdfReader(pdf);
        PdfTextExtractor extractor = new PdfTextExtractor(reader);
        StringBuilder text = new StringBuilder();
        for (int page = 1; page <= reader.getNumberOfPages(); page++) {
            text.append(extractor.getTextFromPage(page)).append('\n');
        }
        return text.toString().replaceAll("\\s+", " ");
    }
}
