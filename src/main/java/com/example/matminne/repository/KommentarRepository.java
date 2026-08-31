package com.example.matminne.repository;

import com.example.matminne.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;

public interface KommentarRepository extends JpaRepository<Kommentar, Long> {
    List<Kommentar> findByOppskriftIdOrderByOpprettetAsc(Long oppskriftId);
    long countByOppskriftId(Long oppskriftId);

    // Bulk: hent antall kommentarer for mange oppskrifter i én spørring
    @Query("SELECT k.oppskriftId, COUNT(k) FROM Kommentar k WHERE k.oppskriftId IN :ids GROUP BY k.oppskriftId")
    List<Object[]> countByOppskriftIdIn(List<Long> ids);
}
