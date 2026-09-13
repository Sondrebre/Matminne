package com.example.matminne.controller;

import com.example.matminne.model.*;
import com.example.matminne.repository.*;
import com.example.matminne.service.BrukerService;
import com.example.matminne.service.SkaperService;
import com.example.matminne.service.StripeConnectService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Skaperstudio: alle kan lage en kokebok, sette egen pris og søke om å selge.
 * MatMinne godkjenner prisen før den kan kjøpes, og utbetaling krever i
 * tillegg fullført Stripe Connect-onboarding.
 */
@Controller
@RequestMapping("/skaper")
public class SkaperController {

    private static final Logger log = LoggerFactory.getLogger(SkaperController.class);

    @Autowired private BrukerService brukerService;
    @Autowired private BrukerRepository brukerRepository;
    @Autowired private SkaperService skaperService;
    @Autowired private StripeConnectService stripeConnect;
    @Autowired private SamlingRepository samlingRepository;
    @Autowired private SamlingOppskriftRepository samlingOppskriftRepository;
    @Autowired private OppskriftRepository oppskriftRepository;
    @Autowired private KjopRepository kjopRepository;

    private Bruker innlogget(OAuth2User principal) {
        if (principal == null) return null;
        return brukerService.finnVedEpost(principal.getAttribute("email"));
    }

    // ── DASHBOARD ─────────────────────────────────────────────────

    @GetMapping
    public String dashboard(Model model, @AuthenticationPrincipal OAuth2User principal) {
        Bruker meg = innlogget(principal);
        if (meg == null) return "redirect:/";

        List<Samling> samlinger = samlingRepository.finnBetalteAvSkaper(meg.getId());
        Map<Long, Long> antallOppskrifter = new HashMap<>();
        Map<Long, Long> antallSalg = new HashMap<>();
        for (Samling s : samlinger) {
            antallOppskrifter.put(s.getId(), samlingOppskriftRepository.countBySamlingId(s.getId()));
            antallSalg.put(s.getId(), kjopRepository.countBySamlingId(s.getId()));
        }

        List<Kjop> salg = skaperService.salgFor(meg.getId());

        model.addAttribute("samlinger", samlinger);
        model.addAttribute("antallOppskrifter", antallOppskrifter);
        model.addAttribute("antallSalg", antallSalg);
        model.addAttribute("salg", salg);
        model.addAttribute("kanSelge", skaperService.kanSelge(meg));
        model.addAttribute("connectStartet",
                meg.getStripeConnectId() != null && !meg.getStripeConnectId().isBlank());
        model.addAttribute("inntjening", SkaperService.kr(skaperService.totalInntjening(meg.getId())));
        model.addAttribute("antallSalgTotalt", salg.size());
        model.addAttribute("plattformAndel", skaperService.getPlattformAndelProsent());
        model.addAttribute("stripeKonfigurert", stripeConnect.erKonfigurert());
        model.addAttribute("harOppskrifter",
                !oppskriftRepository.findByBrukerId(meg.getId()).isEmpty());
        return "skaper";
    }

    // ── STRIPE CONNECT ONBOARDING ─────────────────────────────────

    @GetMapping("/onboarding")
    public String onboarding(@AuthenticationPrincipal OAuth2User principal) {
        Bruker meg = innlogget(principal);
        if (meg == null) return "redirect:/";
        if (!stripeConnect.erKonfigurert()) return "redirect:/skaper?feil=stripe";

        try {
            String url = stripeConnect.startOnboarding(meg);
            brukerRepository.save(meg);   // lagrer ny stripeConnectId
            return "redirect:" + url;
        } catch (Exception e) {
            log.error("Connect-onboarding feilet for {}: {}", meg.getEpost(), e.getMessage());
            return "redirect:/skaper?feil=onboarding";
        }
    }

    @GetMapping("/onboarding/retur")
    public String onboardingRetur(@AuthenticationPrincipal OAuth2User principal) {
        Bruker meg = innlogget(principal);
        if (meg == null) return "redirect:/";

        boolean klar = stripeConnect.sjekkOmKlar(meg.getStripeConnectId());
        meg.setConnectKlar(klar);
        brukerRepository.save(meg);
        log.info("Connect-status for {}: {}", meg.getEpost(), klar ? "klar" : "ikke fullført");

        return klar ? "redirect:/skaper?klar=true" : "redirect:/skaper?ufullstendig=true";
    }

    @GetMapping("/utbetalinger")
    public String utbetalinger(@AuthenticationPrincipal OAuth2User principal) {
        Bruker meg = innlogget(principal);
        if (meg == null || !skaperService.kanSelge(meg)) return "redirect:/skaper";
        try {
            return "redirect:" + stripeConnect.dashboardLenke(meg.getStripeConnectId());
        } catch (Exception e) {
            log.error("Kunne ikke lage dashboard-lenke: {}", e.getMessage());
            return "redirect:/skaper?feil=dashboard";
        }
    }

    // ── KOKEBØKER ─────────────────────────────────────────────────

    @PostMapping("/samling/ny")
    public String nySamling(@RequestParam String navn,
                            @RequestParam(required = false) String beskrivelse,
                            @RequestParam(required = false) String bildeUrl,
                            @RequestParam Integer prisKroner,
                            @AuthenticationPrincipal OAuth2User principal) {
        Bruker meg = innlogget(principal);
        if (meg == null) return "redirect:/";
        if (navn == null || navn.isBlank()) return "redirect:/skaper?feil=navn";
        if (prisKroner == null || prisKroner < 1) return "redirect:/skaper?feil=pris";

        Samling s = new Samling();
        s.setBrukerId(meg.getId());
        s.setNavn(navn.trim());
        s.setBeskrivelse(beskrivelse != null ? beskrivelse.trim() : null);
        s.setBildeUrl(bildeUrl != null && !bildeUrl.isBlank() ? bildeUrl.trim() : null);
        s.setPris(prisKroner * 100);
        s.setStatus(SamlingStatus.UTKAST);
        samlingRepository.save(s);
        return "redirect:/skaper/samling/" + s.getId();
    }

    /** Henter kokeboka hvis den finnes og innlogget bruker eier den. */
    private Samling minSamling(Long id, Bruker meg) {
        if (meg == null) return null;
        Samling s = samlingRepository.findById(id).orElse(null);
        if (s == null || s.getBrukerId() == null || !s.getBrukerId().equals(meg.getId())) return null;
        return s;
    }

    @GetMapping("/samling/{id}")
    public String redigerSamling(@PathVariable Long id, Model model,
                                 @AuthenticationPrincipal OAuth2User principal) {
        Bruker meg = innlogget(principal);
        Samling s = minSamling(id, meg);
        if (s == null) return "redirect:/skaper";

        List<Long> iSamling = samlingOppskriftRepository.findBySamlingId(id)
                .stream().map(SamlingOppskrift::getOppskriftId).collect(Collectors.toList());

        List<Oppskrift> valgte = iSamling.isEmpty()
                ? new ArrayList<>() : oppskriftRepository.findAllById(iSamling);

        List<Oppskrift> tilgjengelige = oppskriftRepository.findByBrukerId(meg.getId())
                .stream().filter(o -> !iSamling.contains(o.getId())).collect(Collectors.toList());

        int andel = skaperService.getPlattformAndelProsent();
        model.addAttribute("samling", s);
        model.addAttribute("valgte", valgte);
        model.addAttribute("tilgjengelige", tilgjengelige);
        model.addAttribute("antallSalg", kjopRepository.countBySamlingId(id));
        model.addAttribute("kanSelge", skaperService.kanSelge(meg));
        model.addAttribute("plattformAndel", andel);
        model.addAttribute("dinAndel", s.skaperAndelKroner(andel));
        return "skaper-samling";
    }

    @PostMapping("/samling/{id}/oppdater")
    public String oppdaterSamling(@PathVariable Long id,
                                  @RequestParam String navn,
                                  @RequestParam(required = false) String beskrivelse,
                                  @RequestParam(required = false) String bildeUrl,
                                  @RequestParam Integer prisKroner,
                                  @AuthenticationPrincipal OAuth2User principal) {
        Bruker meg = innlogget(principal);
        Samling s = minSamling(id, meg);
        if (s == null) return "redirect:/skaper";

        if (navn != null && !navn.isBlank()) s.setNavn(navn.trim());
        s.setBeskrivelse(beskrivelse != null ? beskrivelse.trim() : null);
        s.setBildeUrl(bildeUrl != null && !bildeUrl.isBlank() ? bildeUrl.trim() : null);

        boolean maaBehandlesPaNytt = false;
        if (prisKroner != null && prisKroner >= 1)
            maaBehandlesPaNytt = skaperService.handterPrisendring(s, prisKroner * 100);

        samlingRepository.save(s);
        return "redirect:/skaper/samling/" + id
                + (maaBehandlesPaNytt ? "?ny-pris=true" : "?lagret=true");
    }

    /** Søknaden: skaperen sender kokeboka til MatMinne for godkjenning. */
    @PostMapping("/samling/{id}/send-inn")
    public String sendInn(@PathVariable Long id, @AuthenticationPrincipal OAuth2User principal) {
        Bruker meg = innlogget(principal);
        Samling s = minSamling(id, meg);
        if (s == null) return "redirect:/skaper";

        long antall = samlingOppskriftRepository.countBySamlingId(id);
        String feil = skaperService.sendInn(s, antall);
        if (feil != null) return "redirect:/skaper/samling/" + id + "?feil=" + feil;

        log.info("Kokebok {} sendt til godkjenning av {}", id, meg.getEpost());
        return "redirect:/skaper/samling/" + id + "?sendt=true";
    }

    @PostMapping("/samling/{id}/trekk-tilbake")
    public String trekkTilbake(@PathVariable Long id, @AuthenticationPrincipal OAuth2User principal) {
        Bruker meg = innlogget(principal);
        Samling s = minSamling(id, meg);
        if (s == null) return "redirect:/skaper";
        skaperService.trekkTilbake(s);
        return "redirect:/skaper/samling/" + id + "?trukket=true";
    }

    @PostMapping("/samling/{id}/legg-til/{oppskriftId}")
    public String leggTil(@PathVariable Long id, @PathVariable Long oppskriftId,
                          @AuthenticationPrincipal OAuth2User principal) {
        Bruker meg = innlogget(principal);
        Samling s = minSamling(id, meg);
        if (s == null) return "redirect:/skaper";

        Oppskrift o = oppskriftRepository.findById(oppskriftId).orElse(null);
        if (o == null || !meg.getId().equals(o.getBrukerId()))
            return "redirect:/skaper/samling/" + id;

        if (!samlingOppskriftRepository.existsBySamlingIdAndOppskriftId(id, oppskriftId))
            samlingOppskriftRepository.save(new SamlingOppskrift(id, oppskriftId));
        return "redirect:/skaper/samling/" + id;
    }

    @PostMapping("/samling/{id}/fjern/{oppskriftId}")
    public String fjern(@PathVariable Long id, @PathVariable Long oppskriftId,
                        @AuthenticationPrincipal OAuth2User principal) {
        Bruker meg = innlogget(principal);
        Samling s = minSamling(id, meg);
        if (s == null) return "redirect:/skaper";

        samlingOppskriftRepository.findBySamlingIdAndOppskriftId(id, oppskriftId)
                .ifPresent(samlingOppskriftRepository::delete);
        return "redirect:/skaper/samling/" + id;
    }

    /**
     * Snarvei fra en oppskrift: lager kokebok hvis skaperen ikke har en i
     * utkast, og legger oppskriften rett inn. Dette er inngangen folk finner.
     */
    @PostMapping("/selg-oppskrift/{oppskriftId}")
    public String selgOppskrift(@PathVariable Long oppskriftId,
                                @AuthenticationPrincipal OAuth2User principal) {
        Bruker meg = innlogget(principal);
        if (meg == null) return "redirect:/";

        Oppskrift o = oppskriftRepository.findById(oppskriftId).orElse(null);
        if (o == null || !meg.getId().equals(o.getBrukerId()))
            return "redirect:/detaljer/" + oppskriftId;

        // Bruk et eksisterende utkast hvis det finnes, ellers lag et nytt
        Samling maal = samlingRepository.finnBetalteAvSkaper(meg.getId()).stream()
                .filter(s -> s.getStatus().kanRedigeres())
                .findFirst()
                .orElse(null);

        if (maal == null) {
            maal = new Samling();
            maal.setBrukerId(meg.getId());
            maal.setNavn("Min kokebok");
            maal.setPris(14900);
            maal.setStatus(SamlingStatus.UTKAST);
            maal.setBildeUrl(o.getBildeUrl());
            samlingRepository.save(maal);
        }

        if (!samlingOppskriftRepository.existsBySamlingIdAndOppskriftId(maal.getId(), oppskriftId))
            samlingOppskriftRepository.save(new SamlingOppskrift(maal.getId(), oppskriftId));

        return "redirect:/skaper/samling/" + maal.getId() + "?lagt-til=true";
    }
}
