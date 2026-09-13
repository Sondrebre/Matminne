package com.example.matminne.repository;

import com.example.matminne.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface SamlingRepository extends JpaRepository<Samling, Long> {
    List<Samling> findByBrukerIdOrderByOpprettetDesc(Long brukerId);

    /** Alle betalte kokebøker en skaper har, uansett status (skaper-dashboard). */
    @Query("SELECT s FROM Samling s WHERE s.brukerId = :brukerId AND s.pris IS NOT NULL AND s.pris > 0 "
         + "ORDER BY s.opprettet DESC")
    List<Samling> finnBetalteAvSkaper(@Param("brukerId") Long brukerId);

    /** Godkjente, kjøpbare kokebøker — det som vises offentlig. */
    @Query("SELECT s FROM Samling s WHERE s.status = com.example.matminne.model.SamlingStatus.GODKJENT "
         + "AND s.pris IS NOT NULL AND s.pris > 0 ORDER BY s.datoBehandlet DESC, s.opprettet DESC")
    List<Samling> finnTilSalgs();

    /** Godkjente kokebøker fra én skaper — vises på profilen hennes. */
    @Query("SELECT s FROM Samling s WHERE s.brukerId = :brukerId "
         + "AND s.status = com.example.matminne.model.SamlingStatus.GODKJENT "
         + "AND s.pris IS NOT NULL AND s.pris > 0 ORDER BY s.opprettet DESC")
    List<Samling> finnTilSalgsAvSkaper(@Param("brukerId") Long brukerId);

    /**
     * Godkjente betalte kokebøker som inneholder en gitt oppskrift.
     * Er listen tom er oppskriften gratis; ellers krever den kjøp.
     */
    @Query("SELECT s FROM Samling s WHERE s.status = com.example.matminne.model.SamlingStatus.GODKJENT "
         + "AND s.pris IS NOT NULL AND s.pris > 0 "
         + "AND s.id IN (SELECT so.samlingId FROM SamlingOppskrift so WHERE so.oppskriftId = :oppskriftId)")
    List<Samling> finnBetalteSomInneholder(@Param("oppskriftId") Long oppskriftId);

    /** Søknadskøen til MatMinne, eldste først så ingen blir liggende. */
    @Query("SELECT s FROM Samling s WHERE s.status = com.example.matminne.model.SamlingStatus.TIL_GODKJENNING "
         + "ORDER BY s.datoSendtInn ASC")
    List<Samling> finnTilGodkjenning();

    @Query("SELECT COUNT(s) FROM Samling s WHERE s.status = com.example.matminne.model.SamlingStatus.TIL_GODKJENNING")
    long antallTilGodkjenning();

    /** Behandlede søknader, nyeste først — historikk i admin. */
    @Query("SELECT s FROM Samling s WHERE s.status IN ("
         + "com.example.matminne.model.SamlingStatus.GODKJENT, "
         + "com.example.matminne.model.SamlingStatus.AVSLATT) "
         + "ORDER BY s.datoBehandlet DESC")
    List<Samling> finnBehandlede();
}
