package dev.kevin.gallbladder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface MealRepository extends JpaRepository<Meal, Long> {

    /** Meals eaten in [from, to). */
    @Query("select m from Meal m where m.eatenAt >= :from and m.eatenAt < :to order by m.eatenAt")
    List<Meal> findEatenBetween(LocalDateTime from, LocalDateTime to);

    /** Undoes a delete. Native SQL because the entity's restriction hides deleted rows from JPQL. Returns rows changed. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update meal set deleted_at = null where id = :id and deleted_at is not null", nativeQuery = true)
    int restore(long id);
}
