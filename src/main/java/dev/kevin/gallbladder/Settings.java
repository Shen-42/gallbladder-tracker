package dev.kevin.gallbladder;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.math.BigDecimal;

/**
 * Single-row table. The threshold has no default on purpose: it should come from a doctor or dietitian.
 * The lookback window has an arbitrary default so the summary works before anything is set.
 */
@Entity
public class Settings {

    public static final long ID = 1L;
    public static final int DEFAULT_LOOKBACK_HOURS = 6;

    @Id
    private Long id = ID;

    private BigDecimal fatThresholdGrams;

    private Integer lookbackHours;

    public BigDecimal getFatThresholdGrams() {
        return fatThresholdGrams;
    }

    public void setFatThresholdGrams(BigDecimal fatThresholdGrams) {
        this.fatThresholdGrams = fatThresholdGrams;
    }

    /** Never null: an empty value means {@link #DEFAULT_LOOKBACK_HOURS}. */
    public Integer getLookbackHours() {
        return lookbackHours != null ? lookbackHours : DEFAULT_LOOKBACK_HOURS;
    }

    public void setLookbackHours(Integer lookbackHours) {
        this.lookbackHours = lookbackHours;
    }
}
