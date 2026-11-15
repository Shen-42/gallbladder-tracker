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

    /** The FoodData Central id, for foods picked from a USDA search. */
    private Long fdcId;

    protected Food() {
    }

    public Food(String name, BigDecimal fatPer100g, FoodSource source) {
        this.name = name;
        this.fatPer100g = fatPer100g;
        this.source = source;
    }

    static Food fromUsda(String name, BigDecimal fatPer100g, long fdcId) {
        Food food = new Food(name, fatPer100g, FoodSource.USDA);
        food.fdcId = fdcId;
        return food;
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

    public Long getFdcId() {
        return fdcId;
    }

    public BigDecimal getPortionGrams() {
        return portionGrams;
    }

    public void setPortionGrams(BigDecimal portionGrams) {
        this.portionGrams = portionGrams;
    }
}
