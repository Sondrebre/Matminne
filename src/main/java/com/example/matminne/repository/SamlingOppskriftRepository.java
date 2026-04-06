package com.example.matminne.repository;

import com.example.matminne.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface SamlingOppskriftRepository extends JpaRepository<SamlingOppskrift, Long> {
    List<SamlingOppskrift> findBySamlingId(Long samlingId);
    Optional<SamlingOppskrift> findBySamlingIdAndOppskriftId(Long samlingId, Long oppskriftId);
    boolean existsBySamlingIdAndOppskriftId(Long samlingId, Long oppskriftId);
    long countBySamlingId(Long samlingId);
}
