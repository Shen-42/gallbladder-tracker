package dev.kevin.gallbladder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface MealRepository extends JpaRepository<Meal, Long> {

    /** Meals eaten in [from, to). */
    @Query("select m from Meal m where m.eatenAt >= :from and m.eatenAt < :to order by m.eatenAt")
    List<Meal> findEatenBetween(LocalDateTime from, LocalDateTime to);
}
