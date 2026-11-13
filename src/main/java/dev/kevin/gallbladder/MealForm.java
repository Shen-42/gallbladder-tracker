package dev.kevin.gallbladder;

import org.springframework.format.annotation.DateTimeFormat;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** What the meal form submits. Rows left completely empty are ignored. */
public class MealForm {

    @DateTimeFormat(pattern = "yyyy-MM-dd'T'HH:mm")
    private LocalDateTime eatenAt;
    private String note;
    private List<Item> items = new ArrayList<>();
    private boolean confirmLarge;

    public enum Unit { GRAMS, PORTIONS }

    /** A form filled in from a saved meal, for editing. Amounts come back as grams. */
    static MealForm from(Meal meal) {
        MealForm form = new MealForm();
        form.eatenAt = meal.getEatenAt();
        form.note = meal.getNote();
        for (MealItem mealItem : meal.getItems()) {
            Item item = new Item();
            item.foodName = mealItem.getFood().getName();
            // "100" rather than "100.00" in the input
            item.amount = new BigDecimal(mealItem.getGrams().stripTrailingZeros().toPlainString());
            form.items.add(item);
            // It was confirmed when first saved; don't ask again.
            form.confirmLarge |= mealItem.getGrams().compareTo(MealService.LARGE_GRAMS) > 0;
        }
        return form;
    }

    public static class Item {
        private String foodName;
        private BigDecimal amount;
        private Unit unit = Unit.GRAMS;
        /** Only needed when the food is new. */
        private BigDecimal fatPer100g;
        /** Only needed for PORTIONS when the food has no portion size yet; also updates it. */
        private BigDecimal portionGrams;

        boolean isBlank() {
            return (foodName == null || foodName.isBlank()) && amount == null
                    && fatPer100g == null && portionGrams == null;
        }

        public String getFoodName() { return foodName; }
        public void setFoodName(String foodName) { this.foodName = foodName; }
        public BigDecimal getAmount() { return amount; }
        public void setAmount(BigDecimal amount) { this.amount = amount; }
        public Unit getUnit() { return unit; }
        public void setUnit(Unit unit) { this.unit = unit; }
        public BigDecimal getFatPer100g() { return fatPer100g; }
        public void setFatPer100g(BigDecimal fatPer100g) { this.fatPer100g = fatPer100g; }
        public BigDecimal getPortionGrams() { return portionGrams; }
        public void setPortionGrams(BigDecimal portionGrams) { this.portionGrams = portionGrams; }
    }

    public LocalDateTime getEatenAt() { return eatenAt; }
    public void setEatenAt(LocalDateTime eatenAt) { this.eatenAt = eatenAt; }
    public String getNote() { return note; }
    public void setNote(String note) { this.note = note; }
    public List<Item> getItems() { return items; }
    public void setItems(List<Item> items) { this.items = items; }
    public boolean isConfirmLarge() { return confirmLarge; }
    public void setConfirmLarge(boolean confirmLarge) { this.confirmLarge = confirmLarge; }
}
