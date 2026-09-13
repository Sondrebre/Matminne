package com.example.matminne.service;

import com.example.matminne.model.Bruker;
import com.example.matminne.model.Kjop;
import com.example.matminne.model.Samling;
import com.example.matminne.repository.KjopRepository;
import com.example.matminne.repository.SamlingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Avgjør hvem som kan selge betalte samlinger, og hvem som har tilgang til
 * innholdet i dem.
 *
 * Skaperstatus er invitasjonsbasert: bare e-poster listet i app.skapere kan
 * opprette betalte samlinger. Det holder programmet lukket uten at vi trenger
 * et admin-grensesnitt.
 */
@Service
public class SkaperService {

    /** Komma-separert liste med e-poster som får selge. Settes som miljøvariabel. */
    @Value("${app.skapere:}")
    private String skapereRaa;

    /** Plattformens andel av hvert salg, i prosent. */
    @Value("${app.plattform.andel:20}")
    private int plattformAndelProsent;

    @Autowired private SamlingRepository samlingRepository;
    @Autowired private KjopRepository kjopRepository;

    // ── SKAPERSTATUS ──────────────────────────────────────────────

    private Set<String> skapere() {
        if (skapereRaa == null || skapereRaa.isBlank()) return Set.of();
        return Arrays.stream(skapereRaa.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
    }

    /** True hvis e-posten er invitert inn i skaperprogrammet. */
    public boolean erSkaper(String epost) {
        if (epost == null || epost.isBlank()) return false;
        return skapere().contains(epost.trim().toLowerCase(Locale.ROOT));
    }

    public boolean erSkaper(Bruker bruker) {
        return bruker != null && erSkaper(bruker.getEpost());
    }

    /** True hvis skaperen faktisk kan ta imot penger (Connect-onboarding fullført). */
    public boolean kanSelge(Bruker bruker) {
        return erSkaper(bruker)
                && bruker.getStripeConnectId() != null
                && !bruker.getStripeConnectId().isBlank()
                && bruker.isConnectKlar();
    }

    public int getPlattformAndelProsent() { return plattformAndelProsent; }

    /** Plattformens kutt av et beløp, i øre. */
    public int plattformAndelAv(int belopOre) {
        return Math.round(belopOre * (plattformAndelProsent / 100f));
    }

    // ── TILGANG ───────────────────────────────────────────────────

    /**
     * Er denne oppskriften låst for brukeren?
     *
     * Regler, i rekkefølge:
     *  1. Eieren ser alltid sitt eget innhold.
     *  2. Ligger oppskriften ikke i en publisert betalt samling, er den gratis.
     *  3. Har brukeren kjøpt en av samlingene som inneholder den, er den åpen.
     *  4. Ellers: låst.
     */
    public boolean erLast(Long oppskriftId, Long oppskriftEierId, Bruker innloggetBruker) {
        if (oppskriftId == null) return false;

        // 1. Eieren
        if (innloggetBruker != null && oppskriftEierId != null
                && oppskriftEierId.equals(innloggetBruker.getId())) {
            return false;
        }

        // 2. Gratis hvis ingen betalt samling inneholder den
        List<Samling> betalte = samlingRepository.finnBetalteSomInneholder(oppskriftId);
        if (betalte.isEmpty()) return false;

        // 3. Kjøpt?
        if (innloggetBruker == null) return true;
        return betalte.stream().noneMatch(
                s -> kjopRepository.existsByBrukerIdAndSamlingId(innloggetBruker.getId(), s.getId()));
    }

    /**
     * Samlingen brukeren må kjøpe for å låse opp en oppskrift.
     * Tom hvis oppskriften ikke er låst.
     */
    public Optional<Samling> samlingSomLaserOpp(Long oppskriftId) {
        return samlingRepository.finnBetalteSomInneholder(oppskriftId).stream().findFirst();
    }

    public boolean harKjopt(Bruker bruker, Long samlingId) {
        if (bruker == null || samlingId == null) return false;
        return kjopRepository.existsByBrukerIdAndSamlingId(bruker.getId(), samlingId);
    }

    /** Har brukeren tilgang til hele samlingen (eier den, eller har kjøpt den)? */
    public boolean harTilgangTilSamling(Bruker bruker, Samling samling) {
        if (samling == null) return false;
        if (!samling.erTilSalgs()) return true;           // gratis/privat samling
        if (bruker == null) return false;
        if (samling.getBrukerId() != null && samling.getBrukerId().equals(bruker.getId())) return true;
        return harKjopt(bruker, samling.getId());
    }

    // ── SKAPER-STATISTIKK ─────────────────────────────────────────

    /** Skaperens totale inntjening i øre, etter at plattformandelen er trukket fra. */
    public int totalInntjening(Long skaperId) {
        return kjopRepository.findBySkaperIdOrderByDatoKjoptDesc(skaperId)
                .stream().mapToInt(Kjop::skaperAndel).sum();
    }

    public List<Kjop> salgFor(Long skaperId) {
        return kjopRepository.findBySkaperIdOrderByDatoKjoptDesc(skaperId);
    }

    /** Formaterer et ørebeløp som "1 234 kr". */
    public static String kr(int ore) {
        return String.format("%,d kr", ore / 100).replace(',', ' ');
    }
}
