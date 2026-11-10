package dev.kevin.gallbladder;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
public class Food {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true)
    private String name;

    @Column(nullable = false)
    private BigDecimal fatPer100g;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private FoodSource source;

    /** Weight of one portion (e.g. one egg), so amounts can be entered as "2 portions". Null until given. */
    private BigDecimal portionGrams;

    protected Food() {
    }

    public Food(String name, BigDecimal fatPer100g, FoodSource source) {
        this.name = name;
        this.fatPer100g = fatPer100g;
        this.source = source;
    }

    public Long getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public BigDecimal getFatPer100g() {
        return fatPer100g;
    }

    public FoodSource getSource() {
        return source;
    }

    public BigDecimal getPortionGrams() {
        return portionGrams;
    }

    public void setPortionGrams(BigDecimal portionGrams) {
        this.portionGrams = portionGrams;
    }
}
