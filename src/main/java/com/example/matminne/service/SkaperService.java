package com.example.matminne.service;

import com.example.matminne.model.Bruker;
import com.example.matminne.model.Kjop;
import com.example.matminne.model.Samling;
import com.example.matminne.model.SamlingStatus;
import com.example.matminne.repository.KjopRepository;
import com.example.matminne.repository.SamlingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Avgjør hvem som kan selge betalte kokebøker, og hvem som har tilgang til
 * innholdet i dem.
 *
 * Alle kan søke om å selge: de setter egen pris og sender inn til
 * godkjenning. MatMinne (app.admin) godkjenner prisen før kokeboka kan
 * selges, og utbetaling krever i tillegg fullført Stripe Connect-onboarding.
 */
@Service
public class SkaperService {

    /** E-poster som kan behandle søknader. Settes som miljøvariabel. */
    @Value("${app.admin:}")
    private String adminRaa;

    /** Plattformens andel av hvert salg, i prosent. */
    @Value("${app.plattform.andel:20}")
    private int plattformAndelProsent;

    @Autowired private SamlingRepository samlingRepository;
    @Autowired private KjopRepository kjopRepository;

    // ── ROLLER ────────────────────────────────────────────────────

    private Set<String> adminEposter() {
        if (adminRaa == null || adminRaa.isBlank()) return Set.of();
        return Arrays.stream(adminRaa.split(","))
                .map(s -> s.trim().toLowerCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
    }

    /** True hvis brukeren kan godkjenne og avslå søknader. */
    public boolean erAdmin(String epost) {
        if (epost == null || epost.isBlank()) return false;
        return adminEposter().contains(epost.trim().toLowerCase(Locale.ROOT));
    }

    public boolean erAdmin(Bruker bruker) {
        return bruker != null && erAdmin(bruker.getEpost());
    }

    /**
     * True hvis skaperen faktisk kan ta imot penger.
     * Alle kan søke, men ingen kan selge før Stripe kan betale ut til dem.
     */
    public boolean kanSelge(Bruker bruker) {
        return bruker != null
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

    // ── GODKJENNINGSLØPET ─────────────────────────────────────────

    /**
     * Sender en kokebok inn til godkjenning.
     * @return null hvis den ble sendt inn, ellers en feilkode til brukeren.
     */
    public String sendInn(Samling s, long antallOppskrifter) {
        if (!s.getStatus().kanSendesInn()) return "allerede-sendt";
        if (!s.erBetalt())                 return "pris";
        if (antallOppskrifter == 0)        return "tom";

        s.setStatus(SamlingStatus.TIL_GODKJENNING);
        s.setDatoSendtInn(LocalDateTime.now());
        s.setAvslagsgrunn(null);
        samlingRepository.save(s);
        return null;
    }

    /** Trekker en innsendt søknad tilbake, slik at skaperen kan redigere videre. */
    public void trekkTilbake(Samling s) {
        if (s.getStatus() == SamlingStatus.TIL_GODKJENNING
                || s.getStatus() == SamlingStatus.GODKJENT) {
            s.setStatus(SamlingStatus.UTKAST);
            samlingRepository.save(s);
        }
    }

    public void godkjenn(Samling s) {
        s.setStatus(SamlingStatus.GODKJENT);
        s.setAvslagsgrunn(null);
        s.setDatoBehandlet(LocalDateTime.now());
        samlingRepository.save(s);
    }

    public void avsla(Samling s, String grunn) {
        s.setStatus(SamlingStatus.AVSLATT);
        s.setAvslagsgrunn(grunn != null && !grunn.isBlank() ? grunn.trim() : "Ingen begrunnelse oppgitt.");
        s.setDatoBehandlet(LocalDateTime.now());
        samlingRepository.save(s);
    }

    /**
     * Kalles når skaperen endrer prisen. Det er prisen MatMinne har godkjent,
     * så en godkjent kokebok må behandles på nytt hvis prisen endres.
     * @return true hvis endringen sendte den tilbake i kø.
     */
    public boolean handterPrisendring(Samling s, int nyPrisOre) {
        Integer gammel = s.getPris();
        s.setPris(nyPrisOre);
        if (s.getStatus() == SamlingStatus.GODKJENT
                && (gammel == null || gammel != nyPrisOre)) {
            s.setStatus(SamlingStatus.TIL_GODKJENNING);
            s.setDatoSendtInn(LocalDateTime.now());
            s.setDatoBehandlet(null);
            return true;
        }
        return false;
    }

    public List<Samling> soknadskoen() { return samlingRepository.finnTilGodkjenning(); }

    public long antallTilGodkjenning() { return samlingRepository.antallTilGodkjenning(); }

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
