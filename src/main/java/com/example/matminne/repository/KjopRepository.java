package com.example.matminne.repository;

import com.example.matminne.model.Kjop;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface KjopRepository extends JpaRepository<Kjop, Long> {

    boolean existsByBrukerIdAndSamlingId(Long brukerId, Long samlingId);

    Optional<Kjop> findByStripeSesjonId(String stripeSesjonId);

    List<Kjop> findByBrukerIdOrderByDatoKjoptDesc(Long brukerId);

    List<Kjop> findBySkaperIdOrderByDatoKjoptDesc(Long skaperId);

    /** Alle samlinger brukeren har kjøpt — brukes til tilgangssjekk i bulk. */
    List<Kjop> findByBrukerId(Long brukerId);

    long countBySamlingId(Long samlingId);
}
