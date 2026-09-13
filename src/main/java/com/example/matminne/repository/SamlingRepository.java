package com.example.matminne.repository;

import com.example.matminne.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SamlingRepository extends JpaRepository<Samling, Long> {
    List<Samling> findByBrukerIdOrderByOpprettetDesc(Long brukerId);

    /** Alle betalte samlinger en skaper har, publisert eller ikke (skaper-dashboard). */
    @Query("SELECT s FROM Samling s WHERE s.brukerId = :brukerId AND s.pris IS NOT NULL AND s.pris > 0 "
         + "ORDER BY s.opprettet DESC")
    List<Samling> finnBetalteAvSkaper(@Param("brukerId") Long brukerId);

    /** Publiserte, kjøpbare samlinger — det som vises offentlig. */
    @Query("SELECT s FROM Samling s WHERE s.erPublisert = true AND s.pris IS NOT NULL AND s.pris > 0 "
         + "ORDER BY s.opprettet DESC")
    List<Samling> finnTilSalgs();

    /** Publiserte, kjøpbare samlinger fra én skaper — vises på profilen hennes. */
    @Query("SELECT s FROM Samling s WHERE s.brukerId = :brukerId AND s.erPublisert = true "
         + "AND s.pris IS NOT NULL AND s.pris > 0 ORDER BY s.opprettet DESC")
    List<Samling> finnTilSalgsAvSkaper(@Param("brukerId") Long brukerId);

    /**
     * Betalte, publiserte samlinger som inneholder en gitt oppskrift.
     * Er listen tom er oppskriften gratis; ellers krever den kjøp.
     */
    @Query("SELECT s FROM Samling s WHERE s.erPublisert = true AND s.pris IS NOT NULL AND s.pris > 0 "
         + "AND s.id IN (SELECT so.samlingId FROM SamlingOppskrift so WHERE so.oppskriftId = :oppskriftId)")
    List<Samling> finnBetalteSomInneholder(@Param("oppskriftId") Long oppskriftId);
}
