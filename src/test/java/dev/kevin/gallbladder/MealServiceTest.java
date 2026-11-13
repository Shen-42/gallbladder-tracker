package dev.kevin.gallbladder;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.validation.BeanPropertyBindingResult;
import org.springframework.validation.Errors;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class MealServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 10, 12, 0);

    @Autowired FoodRepository foods;
    @Autowired MealRepository meals;
    @Autowired TestEntityManager em;
    MealService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneId.of("UTC"));
        service = new MealService(foods, meals, clock);
    }

    @Test
    void savesMealAndCreatesNewFoodInline() {
        MealForm form = form(NOW.minusHours(1), item("Fried egg", "100", MealForm.Unit.GRAMS, "15", null));

        Meal meal = service.log(form, errors(form)).orElseThrow();

        assertThat(meal.fatTotal()).isEqualByComparingTo("15");
        Food egg = foods.findByNameIgnoreCase("fried egg").orElseThrow();
        assertThat(egg.getSource()).isEqualTo(FoodSource.CUSTOM);
        assertThat(egg.getFatPer100g()).isEqualByComparingTo("15");
    }

    @Test
    void reusesExistingFoodIgnoringCase() {
        foods.save(new Food("Rice", new BigDecimal("0.3"), FoodSource.CUSTOM));
        MealForm form = form(NOW, item("rice", "200", MealForm.Unit.GRAMS, null, null));

        Meal meal = service.log(form, errors(form)).orElseThrow();

        assertThat(foods.count()).isEqualTo(1);
        assertThat(meal.fatTotal()).isEqualByComparingTo("0.6");
    }

    @Test
    void portionsConvertToGramsAndPortionSizeIsRemembered() {
        MealForm first = form(NOW, item("Egg", "2", MealForm.Unit.PORTIONS, "10", "50"));
        Meal meal = service.log(first, errors(first)).orElseThrow();
        assertThat(meal.getItems().getFirst().getGrams()).isEqualByComparingTo("100");

        MealForm second = form(NOW, item("Egg", "3", MealForm.Unit.PORTIONS, null, null));
        Meal again = service.log(second, errors(second)).orElseThrow();
        assertThat(again.getItems().getFirst().getGrams()).isEqualByComparingTo("150");
    }

    @Test
    void portionsWithoutAKnownSizeAreRejected() {
        MealForm form = form(NOW, item("Egg", "2", MealForm.Unit.PORTIONS, "10", null));
        Errors errors = errors(form);

        assertThat(service.log(form, errors)).isEmpty();
        assertThat(errors.getFieldError("items[0].portionGrams")).isNotNull();
        assertThat(foods.count()).isZero();
    }

    @Test
    void futureTimeIsRejectedAndNothingSaved() {
        MealForm form = form(NOW.plusMinutes(1), item("Toast", "50", MealForm.Unit.GRAMS, "4", null));
        Errors errors = errors(form);

        assertThat(service.log(form, errors)).isEmpty();
        assertThat(errors.getFieldError("eatenAt")).isNotNull();
        assertThat(meals.count()).isZero();
        assertThat(foods.count()).isZero();
    }

    @Test
    void newFoodWithoutFatValueIsRejected() {
        MealForm form = form(NOW, item("Mystery stew", "300", MealForm.Unit.GRAMS, null, null));
        Errors errors = errors(form);

        assertThat(service.log(form, errors)).isEmpty();
        assertThat(errors.getFieldError("items[0].fatPer100g")).isNotNull();
    }

    @Test
    void over2000GramsNeedsConfirmation() {
        MealForm form = form(NOW, item("Soup", "2500", MealForm.Unit.GRAMS, "1", null));
        Errors errors = errors(form);

        assertThat(service.log(form, errors)).isEmpty();
        assertThat(errors.getFieldError("items[0].amount").getCode()).isEqualTo("tooLarge");

        form.setConfirmLarge(true);
        assertThat(service.log(form, errors(form))).isPresent();
    }

    @Test
    void formWithOnlyBlankRowsIsRejected() {
        MealForm form = form(NOW, new MealForm.Item());
        Errors errors = errors(form);

        assertThat(service.log(form, errors)).isEmpty();
        assertThat(errors.getGlobalError()).isNotNull();
    }

    @Test
    void updateReplacesTimeNoteAndFoods() {
        MealForm original = form(NOW.minusHours(3), item("Egg", "100", MealForm.Unit.GRAMS, "10", null));
        long id = service.log(original, errors(original)).orElseThrow().getId();

        MealForm edited = form(NOW.minusHours(2),
                item("Rice", "200", MealForm.Unit.GRAMS, "0.5", null),
                item("egg", "50", MealForm.Unit.GRAMS, null, null));
        edited.setNote("lunch");
        service.update(id, edited, errors(edited)).orElseThrow();
        reloadFromDatabase();

        Meal meal = meals.findById(id).orElseThrow();
        assertThat(meal.getEatenAt()).isEqualTo(NOW.minusHours(2));
        assertThat(meal.getNote()).isEqualTo("lunch");
        assertThat(meal.getItems()).extracting(i -> i.getFood().getName()).containsExactly("Rice", "Egg");
        assertThat(meal.fatTotal()).isEqualByComparingTo("6");
    }

    @Test
    void invalidUpdateChangesNothing() {
        MealForm original = form(NOW.minusHours(3), item("Egg", "100", MealForm.Unit.GRAMS, "10", null));
        long id = service.log(original, errors(original)).orElseThrow().getId();

        MealForm edited = form(NOW.plusHours(1), item("Egg", "300", MealForm.Unit.GRAMS, null, null));
        Errors errors = errors(edited);
        assertThat(service.update(id, edited, errors)).isEmpty();
        assertThat(errors.getFieldError("eatenAt")).isNotNull();
        reloadFromDatabase();

        Meal meal = meals.findById(id).orElseThrow();
        assertThat(meal.getEatenAt()).isEqualTo(NOW.minusHours(3));
        assertThat(meal.getItems()).extracting(MealItem::getGrams).usingElementComparator(BigDecimal::compareTo)
                .containsExactly(new BigDecimal("100"));
    }

    @Test
    void deleteHidesMealAndRestoreBringsItBackWithItsFoods() {
        MealForm form = form(NOW, item("Egg", "100", MealForm.Unit.GRAMS, "10", null));
        long id = service.log(form, errors(form)).orElseThrow().getId();

        service.delete(id);
        reloadFromDatabase();
        assertThat(meals.findById(id)).isEmpty();
        assertThat(meals.findEatenBetween(NOW.minusDays(1), NOW.plusDays(1))).isEmpty();

        service.restore(id);
        reloadFromDatabase();
        assertThat(meals.findById(id).orElseThrow().fatTotal()).isEqualByComparingTo("10");
    }

    @Test
    void editedFormStartsFromTheSavedMeal() {
        MealForm original = form(NOW, item("Soup", "2500", MealForm.Unit.GRAMS, "1", null));
        original.setConfirmLarge(true);
        original.setNote("dinner");
        Meal meal = service.log(original, errors(original)).orElseThrow();

        MealForm form = MealForm.from(meal);

        assertThat(form.getEatenAt()).isEqualTo(NOW);
        assertThat(form.getNote()).isEqualTo("dinner");
        assertThat(form.getItems()).singleElement().satisfies(i -> {
            assertThat(i.getFoodName()).isEqualTo("Soup");
            assertThat(i.getAmount()).isEqualByComparingTo("2500");
        });
        assertThat(form.isConfirmLarge()).as("already confirmed when first saved").isTrue();
    }

    private void reloadFromDatabase() {
        em.flush();
        em.clear();
    }

    private static MealForm form(LocalDateTime eatenAt, MealForm.Item... items) {
        MealForm form = new MealForm();
        form.setEatenAt(eatenAt);
        form.getItems().addAll(java.util.List.of(items));
        return form;
    }

    private static MealForm.Item item(String name, String amount, MealForm.Unit unit, String fat, String portionGrams) {
        MealForm.Item item = new MealForm.Item();
        item.setFoodName(name);
        item.setAmount(new BigDecimal(amount));
        item.setUnit(unit);
        item.setFatPer100g(fat == null ? null : new BigDecimal(fat));
        item.setPortionGrams(portionGrams == null ? null : new BigDecimal(portionGrams));
        return item;
    }

    private static Errors errors(MealForm form) {
        return new BeanPropertyBindingResult(form, "mealForm");
    }
}
