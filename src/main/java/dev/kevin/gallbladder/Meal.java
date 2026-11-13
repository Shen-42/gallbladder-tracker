package dev.kevin.gallbladder;

import jakarta.persistence.*;
import org.hibernate.annotations.SQLRestriction;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Entity
@SQLRestriction("deleted_at is null") // deleted meals are hidden from every query until restored
public class Meal {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private LocalDateTime eatenAt;

    private String note;

    private LocalDateTime deletedAt;

    @OneToMany(mappedBy = "meal", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("id")
    private List<MealItem> items = new ArrayList<>();

    protected Meal() {
    }

    public Meal(LocalDateTime eatenAt, String note) {
        this.eatenAt = eatenAt;
        this.note = note;
    }

    public void update(LocalDateTime eatenAt, String note) {
        this.eatenAt = eatenAt;
        this.note = note;
    }

    public void markDeleted(LocalDateTime when) {
        this.deletedAt = when;
    }

    public void addItem(Food food, BigDecimal grams) {
        items.add(new MealItem(this, food, grams));
    }

    /** Computed on demand, never stored. */
    public BigDecimal fatTotal() {
        return items.stream().map(MealItem::fatGrams).reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public Long getId() {
        return id;
    }

    public LocalDateTime getEatenAt() {
        return eatenAt;
    }

    public String getNote() {
        return note;
    }

    public List<MealItem> getItems() {
        return items;
    }
}
