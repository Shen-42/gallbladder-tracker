package dev.kevin.gallbladder;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.math.BigDecimal;

/**
 * Single-row table. The fat limit defaults to 10 g, the top of the 5 to 10 g per meal often advised during a
 * cholecystitis flare-up or recovery; a doctor or dietitian may give a different one. The lookback window has an
 * arbitrary default so the summary works before anything is set.
 */
@Entity
public class Settings {

    public static final long ID = 1L;
    public static final BigDecimal DEFAULT_FAT_THRESHOLD_GRAMS = BigDecimal.TEN;
    public static final int DEFAULT_LOOKBACK_HOURS = 6;

    @Id
    private Long id = ID;

    private BigDecimal fatThresholdGrams;

    private Integer lookbackHours;

    /** Never null: an empty value means {@link #DEFAULT_FAT_THRESHOLD_GRAMS}. */
    public BigDecimal getFatThresholdGrams() {
        return fatThresholdGrams != null ? fatThresholdGrams : DEFAULT_FAT_THRESHOLD_GRAMS;
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
