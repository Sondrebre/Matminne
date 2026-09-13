package com.example.matminne.controller;

import com.example.matminne.model.*;
import com.example.matminne.repository.*;
import com.example.matminne.service.BrukerService;
import com.example.matminne.service.KjopService;
import com.example.matminne.service.SkaperService;
import com.example.matminne.service.StripeConnectService;
import com.stripe.model.checkout.Session;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Offentlig salgsside for betalte samlinger, og kjøpsflyten.
 */
@Controller
public class SamlingKjopController {

    private static final Logger log = LoggerFactory.getLogger(SamlingKjopController.class);

    @Autowired private BrukerService brukerService;
    @Autowired private SkaperService skaperService;
    @Autowired private StripeConnectService stripeConnect;
    @Autowired private KjopService kjopService;
    @Autowired private SamlingRepository samlingRepository;
    @Autowired private SamlingOppskriftRepository samlingOppskriftRepository;
    @Autowired private OppskriftRepository oppskriftRepository;
    @Autowired private KjopRepository kjopRepository;

    private Bruker innlogget(OAuth2User principal) {
        if (principal == null) return null;
        return brukerService.finnVedEpost(principal.getAttribute("email"));
    }

    // ── OFFENTLIG SALGSSIDE ───────────────────────────────────────

    @GetMapping("/samling/{id}")
    public String visSamling(@PathVariable Long id, Model model,
                             @AuthenticationPrincipal OAuth2User principal) {
        Samling s = samlingRepository.findById(id).orElse(null);
        if (s == null) return "redirect:/utforsk";

        Bruker meg = innlogget(principal);
        boolean erEier = meg != null && s.getBrukerId() != null && s.getBrukerId().equals(meg.getId());

        // Upubliserte samlinger er bare synlige for eieren
        if (!s.erTilSalgs() && !erEier) return "redirect:/utforsk";

        Bruker skaper = brukerService.findById(s.getBrukerId());
        boolean harTilgang = skaperService.harTilgangTilSamling(meg, s);

        List<Long> ids = samlingOppskriftRepository.findBySamlingId(id)
                .stream().map(SamlingOppskrift::getOppskriftId).collect(Collectors.toList());
        List<Oppskrift> oppskrifter = ids.isEmpty()
                ? new ArrayList<>() : oppskriftRepository.findAllById(ids);

        model.addAttribute("samling", s);
        model.addAttribute("skaper", skaper);
        model.addAttribute("oppskrifter", oppskrifter);
        model.addAttribute("antall", oppskrifter.size());
        model.addAttribute("harTilgang", harTilgang);
        model.addAttribute("erEier", erEier);
        model.addAttribute("innlogget", meg != null);
        model.addAttribute("brukerEpost", meg != null ? meg.getEpost() : null);
        model.addAttribute("profilBilde", meg != null ? meg.getBildeUrl() : null);
        return "samling-salg";
    }

    // ── KJØP ──────────────────────────────────────────────────────

    @PostMapping("/samling/{id}/kjop")
    public String kjop(@PathVariable Long id, @AuthenticationPrincipal OAuth2User principal) {
        Bruker meg = innlogget(principal);
        if (meg == null) return "redirect:/";

        Samling s = samlingRepository.findById(id).orElse(null);
        if (s == null || !s.erTilSalgs()) return "redirect:/utforsk";

        // Allerede kjøpt, eller sin egen samling
        if (skaperService.harTilgangTilSamling(meg, s)) return "redirect:/samling/" + id;

        Bruker skaper = brukerService.findById(s.getBrukerId());
        if (skaper == null || !skaperService.kanSelge(skaper)) {
            log.warn("Kjøp avvist: skaper {} kan ikke motta betaling", s.getBrukerId());
            return "redirect:/samling/" + id + "?feil=utilgjengelig";
        }
        if (!stripeConnect.erKonfigurert()) return "redirect:/samling/" + id + "?feil=stripe";

        try {
            return "redirect:" + stripeConnect.startKjop(s, meg, skaper);
        } catch (Exception e) {
            log.error("Checkout feilet for samling {}: {}", id, e.getMessage());
            return "redirect:/samling/" + id + "?feil=teknisk";
        }
    }

    @GetMapping("/samling/kjop/suksess")
    public String suksess(@RequestParam String session_id,
                          @AuthenticationPrincipal OAuth2User principal) {
        Bruker meg = innlogget(principal);
        if (meg == null) return "redirect:/";

        try {
            Long samlingId = kjopService.registrer(Session.retrieve(session_id));
            if (samlingId != null) return "redirect:/samling/" + samlingId + "?kjopt=true";
        } catch (Exception e) {
            log.error("Kunne ikke verifisere kjøp {}: {}", session_id, e.getMessage());
        }
        return "redirect:/mine-kjop?uklart=true";
    }

    // ── MINE KJØP ─────────────────────────────────────────────────

    @GetMapping("/mine-kjop")
    public String mineKjop(Model model, @AuthenticationPrincipal OAuth2User principal) {
        Bruker meg = innlogget(principal);
        if (meg == null) return "redirect:/";

        List<Kjop> kjop = kjopRepository.findByBrukerIdOrderByDatoKjoptDesc(meg.getId());
        List<Samling> samlinger = kjop.isEmpty() ? new ArrayList<>()
                : samlingRepository.findAllById(kjop.stream().map(Kjop::getSamlingId).collect(Collectors.toList()));

        // Oppslag på id, slik at malen kan hente forsidebildet uten projeksjon
        Map<Long, Samling> samlingPerId = samlinger.stream()
                .collect(Collectors.toMap(Samling::getId, s -> s, (a, b) -> a));

        model.addAttribute("kjop", kjop);
        model.addAttribute("samlingPerId", samlingPerId);
        model.addAttribute("brukerEpost", meg.getEpost());
        model.addAttribute("profilBilde", meg.getBildeUrl());
        return "mine-kjop";
    }
}
