package com.example.matminne;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import java.util.List;

public interface LikeRepository extends JpaRepository<Like, Long> {
    Like findByBrukerIdAndOppskriftId(Long brukerId, Long oppskriftId);
    boolean existsByBrukerIdAndOppskriftId(Long brukerId, Long oppskriftId);
    long countByOppskriftId(Long oppskriftId);

    @Query("SELECT l.oppskriftId FROM Like l GROUP BY l.oppskriftId ORDER BY COUNT(l) DESC")
    List<Long> findTopLikedOppskriftIds(Pageable pageable);
}
