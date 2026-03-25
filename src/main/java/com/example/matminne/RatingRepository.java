package com.example.matminne;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface RatingRepository extends JpaRepository<Rating, Long> {
    Rating findByBrukerIdAndOppskriftId(Long brukerId, Long oppskriftId);
    long countByOppskriftId(Long oppskriftId);

    @Query("SELECT COALESCE(AVG(r.verdi), 0) FROM Rating r WHERE r.oppskriftId = :oppskriftId")
    double snitRatingForOppskrift(Long oppskriftId);
}
