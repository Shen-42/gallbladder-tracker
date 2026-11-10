package dev.kevin.gallbladder;

import jakarta.persistence.*;

import java.math.BigDecimal;

@Entity
public class MealItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false)
    private Meal meal;

    @ManyToOne(optional = false)
    private Food food;

    @Column(nullable = false)
    private BigDecimal grams;

    protected MealItem() {
    }

    MealItem(Meal meal, Food food, BigDecimal grams) {
        this.meal = meal;
        this.food = food;
        this.grams = grams;
    }

    public BigDecimal fatGrams() {
        return grams.multiply(food.getFatPer100g()).movePointLeft(2);
    }

    public Food getFood() {
        return food;
    }

    public BigDecimal getGrams() {
        return grams;
    }
}
