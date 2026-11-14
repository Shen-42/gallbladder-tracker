package dev.kevin.gallbladder;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.Errors;

import java.math.BigDecimal;

@Service
public class SettingsService {

    private final SettingsRepository settings;

    public SettingsService(SettingsRepository settings) {
        this.settings = settings;
    }

    /**
     * Checks the values, recording problems in {@code errors} next to the field they belong to.
     * Saves nothing unless both are valid. Either value may be left empty.
     */
    @Transactional
    public boolean save(Settings form, Errors errors) {
        BigDecimal threshold = form.getFatThresholdGrams();
        if (threshold != null && threshold.signum() <= 0) {
            errors.rejectValue("fatThresholdGrams", "positive", "Enter a limit above 0 g, or leave it empty.");
        }
        Integer hours = form.getLookbackHours();
        if (hours != null && hours < 1) {
            errors.rejectValue("lookbackHours", "positive", "Enter at least 1 hour, or leave it empty.");
        }
        if (errors.hasErrors()) {
            return false;
        }
        settings.save(form);
        return true;
    }
}
