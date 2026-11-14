package dev.kevin.gallbladder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class SettingsServiceTest {

    @Autowired SettingsRepository settings;
    @Autowired TestEntityManager em;
    SettingsService service;

    @BeforeEach
    void setUp() {
        service = new SettingsService(settings);
    }

    @Test
    void nothingIsSetByDefault() {
        Settings current = settings.current();

        assertThat(current.getFatThresholdGrams()).isNull();
        assertThat(current.getLookbackHours()).isNull();
    }

    @Test
    void savesBothValuesInASingleRow() {
        save("15", 6);
        save("12.5", 4);
        reloadFromDatabase();

        assertThat(settings.count()).isOne();
        assertThat(settings.current().getFatThresholdGrams()).isEqualByComparingTo("12.5");
        assertThat(settings.current().getLookbackHours()).isEqualTo(4);
    }

    @Test
    void valuesCanBeClearedAgain() {
        save("15", 6);
        save(null, null);
        reloadFromDatabase();

        assertThat(settings.current().getFatThresholdGrams()).isNull();
        assertThat(settings.current().getLookbackHours()).isNull();
    }

    @Test
    void zeroOrNegativeValuesAreRejectedAndNothingChanges() {
        save("15", 6);

        Settings form = form("0", 0);
        Errors errors = errors(form);
        assertThat(service.save(form, errors)).isFalse();
        assertThat(errors.getFieldError("fatThresholdGrams")).isNotNull();
        assertThat(errors.getFieldError("lookbackHours")).isNotNull();

        form = form("-1", -1);
        errors = errors(form);
        assertThat(service.save(form, errors)).isFalse();
        assertThat(errors.getErrorCount()).isEqualTo(2);
        reloadFromDatabase();

        assertThat(settings.current().getFatThresholdGrams()).isEqualByComparingTo("15");
        assertThat(settings.current().getLookbackHours()).isEqualTo(6);
    }

    private void save(String threshold, Integer hours) {
        Settings form = form(threshold, hours);
        assertThat(service.save(form, errors(form))).isTrue();
    }

    private void reloadFromDatabase() {
        em.flush();
        em.clear();
    }

    private static Settings form(String threshold, Integer hours) {
        Settings form = new Settings();
        form.setFatThresholdGrams(threshold == null ? null : new BigDecimal(threshold));
        form.setLookbackHours(hours);
        return form;
    }

    private static Errors errors(Settings form) {
        return new BeanPropertyBindingResult(form, "settings");
    }
}
