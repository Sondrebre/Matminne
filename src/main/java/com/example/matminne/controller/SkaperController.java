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
 * Skaperprogrammet: onboarding mot Stripe, administrasjon av betalte
 * samlinger, og salgsoversikt.
 *
 * Alle ruter krever at brukeren er invitert inn (app.skapere).
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
        if (!skaperService.erSkaper(meg)) return "redirect:/kokebok";

        List<Samling> samlinger = samlingRepository.finnBetalteAvSkaper(meg.getId());
        Map<Long, Long> antallOppskrifter = new HashMap<>();
        Map<Long, Long> antallSalg = new HashMap<>();
        for (Samling s : samlinger) {
            antallOppskrifter.put(s.getId(), samlingOppskriftRepository.countBySamlingId(s.getId()));
            antallSalg.put(s.getId(), kjopRepository.countBySamlingId(s.getId()));
        }

        List<Kjop> salg = skaperService.salgFor(meg.getId());

        model.addAttribute("brukerEpost", meg.getEpost());
        model.addAttribute("profilBilde", meg.getBildeUrl());
        model.addAttribute("samlinger", samlinger);
        model.addAttribute("antallOppskrifter", antallOppskrifter);
        model.addAttribute("antallSalg", antallSalg);
        model.addAttribute("salg", salg);
        model.addAttribute("kanSelge", skaperService.kanSelge(meg));
        model.addAttribute("connectStartet", meg.getStripeConnectId() != null && !meg.getStripeConnectId().isBlank());
        model.addAttribute("inntjening", SkaperService.kr(skaperService.totalInntjening(meg.getId())));
        model.addAttribute("antallSalgTotalt", salg.size());
        model.addAttribute("plattformAndel", skaperService.getPlattformAndelProsent());
        model.addAttribute("stripeKonfigurert", stripeConnect.erKonfigurert());
        return "skaper";
    }

    // ── STRIPE CONNECT ONBOARDING ─────────────────────────────────

    @GetMapping("/onboarding")
    public String onboarding(@AuthenticationPrincipal OAuth2User principal) {
        Bruker meg = innlogget(principal);
        if (meg == null) return "redirect:/";
        if (!skaperService.erSkaper(meg)) return "redirect:/kokebok";
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

    // ── BETALTE SAMLINGER ─────────────────────────────────────────

    @PostMapping("/samling/ny")
    public String nySamling(@RequestParam String navn,
                            @RequestParam(required = false) String beskrivelse,
                            @RequestParam(required = false) String bildeUrl,
                            @RequestParam Integer prisKroner,
                            @AuthenticationPrincipal OAuth2User principal) {
        Bruker meg = innlogget(principal);
        if (meg == null || !skaperService.erSkaper(meg)) return "redirect:/kokebok";
        if (navn == null || navn.isBlank()) return "redirect:/skaper?feil=navn";
        if (prisKroner == null || prisKroner < 1) return "redirect:/skaper?feil=pris";

        Samling s = new Samling();
        s.setBrukerId(meg.getId());
        s.setNavn(navn.trim());
        s.setBeskrivelse(beskrivelse != null ? beskrivelse.trim() : null);
        s.setBildeUrl(bildeUrl != null && !bildeUrl.isBlank() ? bildeUrl.trim() : null);
        s.setPris(prisKroner * 100);
        s.setErPublisert(false);
        samlingRepository.save(s);
        return "redirect:/skaper/samling/" + s.getId();
    }

    @GetMapping("/samling/{id}")
    public String redigerSamling(@PathVariable Long id, Model model,
                                 @AuthenticationPrincipal OAuth2User principal) {
        Bruker meg = innlogget(principal);
        if (meg == null || !skaperService.erSkaper(meg)) return "redirect:/kokebok";
        Samling s = samlingRepository.findById(id).orElse(null);
        if (s == null || !s.getBrukerId().equals(meg.getId())) return "redirect:/skaper";

        List<Long> iSamling = samlingOppskriftRepository.findBySamlingId(id)
                .stream().map(SamlingOppskrift::getOppskriftId).collect(Collectors.toList());

        List<Oppskrift> valgte = iSamling.isEmpty()
                ? new ArrayList<>() : oppskriftRepository.findAllById(iSamling);

        // Skaperens øvrige oppskrifter, som kan legges til
        List<Oppskrift> tilgjengelige = oppskriftRepository.findByBrukerId(meg.getId())
                .stream().filter(o -> !iSamling.contains(o.getId())).collect(Collectors.toList());

        model.addAttribute("brukerEpost", meg.getEpost());
        model.addAttribute("profilBilde", meg.getBildeUrl());
        model.addAttribute("samling", s);
        model.addAttribute("valgte", valgte);
        model.addAttribute("tilgjengelige", tilgjengelige);
        model.addAttribute("antallSalg", kjopRepository.countBySamlingId(id));
        model.addAttribute("kanSelge", skaperService.kanSelge(meg));
        model.addAttribute("plattformAndel", skaperService.getPlattformAndelProsent());
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
        if (meg == null || !skaperService.erSkaper(meg)) return "redirect:/kokebok";
        Samling s = samlingRepository.findById(id).orElse(null);
        if (s == null || !s.getBrukerId().equals(meg.getId())) return "redirect:/skaper";

        if (navn != null && !navn.isBlank()) s.setNavn(navn.trim());
        s.setBeskrivelse(beskrivelse != null ? beskrivelse.trim() : null);
        s.setBildeUrl(bildeUrl != null && !bildeUrl.isBlank() ? bildeUrl.trim() : null);
        if (prisKroner != null && prisKroner >= 1) s.setPris(prisKroner * 100);
        samlingRepository.save(s);
        return "redirect:/skaper/samling/" + id + "?lagret=true";
    }

    @PostMapping("/samling/{id}/publiser")
    public String publiser(@PathVariable Long id, @AuthenticationPrincipal OAuth2User principal) {
        Bruker meg = innlogget(principal);
        if (meg == null || !skaperService.erSkaper(meg)) return "redirect:/kokebok";
        Samling s = samlingRepository.findById(id).orElse(null);
        if (s == null || !s.getBrukerId().equals(meg.getId())) return "redirect:/skaper";

        // Kan ikke selge før Stripe kan betale ut til henne
        if (!skaperService.kanSelge(meg))
            return "redirect:/skaper/samling/" + id + "?feil=ikke-klar";
        if (samlingOppskriftRepository.countBySamlingId(id) == 0)
            return "redirect:/skaper/samling/" + id + "?feil=tom";

        s.setErPublisert(true);
        samlingRepository.save(s);
        log.info("Samling {} publisert av {}", id, meg.getEpost());
        return "redirect:/skaper/samling/" + id + "?publisert=true";
    }

    @PostMapping("/samling/{id}/avpubliser")
    public String avpubliser(@PathVariable Long id, @AuthenticationPrincipal OAuth2User principal) {
        Bruker meg = innlogget(principal);
        if (meg == null || !skaperService.erSkaper(meg)) return "redirect:/kokebok";
        Samling s = samlingRepository.findById(id).orElse(null);
        if (s == null || !s.getBrukerId().equals(meg.getId())) return "redirect:/skaper";
        s.setErPublisert(false);
        samlingRepository.save(s);
        return "redirect:/skaper/samling/" + id;
    }

    @PostMapping("/samling/{id}/legg-til/{oppskriftId}")
    public String leggTil(@PathVariable Long id, @PathVariable Long oppskriftId,
                          @AuthenticationPrincipal OAuth2User principal) {
        Bruker meg = innlogget(principal);
        if (meg == null || !skaperService.erSkaper(meg)) return "redirect:/kokebok";
        Samling s = samlingRepository.findById(id).orElse(null);
        Oppskrift o = oppskriftRepository.findById(oppskriftId).orElse(null);
        if (s == null || !s.getBrukerId().equals(meg.getId())) return "redirect:/skaper";
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
        if (meg == null || !skaperService.erSkaper(meg)) return "redirect:/kokebok";
        Samling s = samlingRepository.findById(id).orElse(null);
        if (s == null || !s.getBrukerId().equals(meg.getId())) return "redirect:/skaper";

        samlingOppskriftRepository.findBySamlingIdAndOppskriftId(id, oppskriftId)
                .ifPresent(samlingOppskriftRepository::delete);
        return "redirect:/skaper/samling/" + id;
    }
}
