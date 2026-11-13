package dev.kevin.gallbladder;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.time.LocalDateTime;
import java.util.List;

public interface EpisodeRepository extends JpaRepository<Episode, Long> {

    /** Episodes that started in [from, to). */
    @Query("select e from Episode e where e.startedAt >= :from and e.startedAt < :to order by e.startedAt")
    List<Episode> findStartedBetween(LocalDateTime from, LocalDateTime to);

    /** Undoes a delete. Native SQL because the entity's restriction hides deleted rows from JPQL. Returns rows changed. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query(value = "update episode set deleted_at = null where id = :id and deleted_at is not null", nativeQuery = true)
    int restore(long id);
}
