package dev.kevin.gallbladder;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

import java.math.BigDecimal;

/** Single-row table. The threshold has no default on purpose: it should come from a doctor or dietitian. */
@Entity
public class Settings {

    public static final long ID = 1L;

    @Id
    private Long id = ID;

    private BigDecimal fatThresholdGrams;

    public BigDecimal getFatThresholdGrams() {
        return fatThresholdGrams;
    }
}
