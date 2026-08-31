package com.example.matminne.repository;

import com.example.matminne.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import java.util.List;
import java.util.Set;

public interface LikeRepository extends JpaRepository<Like, Long> {
    Like findByBrukerIdAndOppskriftId(Long brukerId, Long oppskriftId);
    boolean existsByBrukerIdAndOppskriftId(Long brukerId, Long oppskriftId);
    @org.springframework.transaction.annotation.Transactional
    void deleteByBrukerId(Long brukerId);
    long countByOppskriftId(Long oppskriftId);

    @Query("SELECT l.oppskriftId FROM Like l GROUP BY l.oppskriftId ORDER BY COUNT(l) DESC")
    List<Long> findTopLikedOppskriftIds(Pageable pageable);

    // Bulk: hent antall likes for mange oppskrifter i én spørring
    @Query("SELECT l.oppskriftId, COUNT(l) FROM Like l WHERE l.oppskriftId IN :ids GROUP BY l.oppskriftId")
    List<Object[]> countByOppskriftIdIn(List<Long> ids);

    // Bulk: hvilke oppskrifter har denne brukeren likt?
    @Query("SELECT l.oppskriftId FROM Like l WHERE l.brukerId = :brukerId AND l.oppskriftId IN :ids")
    Set<Long> findLikedIdsByBrukerIdAndOppskriftIdIn(Long brukerId, List<Long> ids);
}
