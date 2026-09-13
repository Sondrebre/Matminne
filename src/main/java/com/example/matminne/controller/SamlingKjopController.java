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
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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

    // ── MARKEDSPLASS ──────────────────────────────────────────────

    /** Alle godkjente kokebøker, sortert. Her finner folk dem som selger. */
    @GetMapping("/kokeboker")
    public String markedsplass(@RequestParam(required = false) String sorter,
                               Model model, @AuthenticationPrincipal OAuth2User principal) {
        Bruker meg = innlogget(principal);
        List<Samling> kokeboker = samlingRepository.finnTilSalgs();

        Map<Long, Bruker> skapere = new HashMap<>();
        Map<Long, Long> antallOppskrifter = new HashMap<>();
        Map<Long, Long> antallSalg = new HashMap<>();
        Set<Long> mine = new HashSet<>();

        for (Samling s : kokeboker) {
            if (s.getBrukerId() != null && !skapere.containsKey(s.getId())) {
                Bruker sk = brukerService.findById(s.getBrukerId());
                if (sk != null) skapere.put(s.getId(), sk);
            }
            antallOppskrifter.put(s.getId(), samlingOppskriftRepository.countBySamlingId(s.getId()));
            antallSalg.put(s.getId(), kjopRepository.countBySamlingId(s.getId()));
            if (meg != null && skaperService.harTilgangTilSamling(meg, s)) mine.add(s.getId());
        }

        // Standard er nyest først (slik repoet leverer); ellers sorter om
        if ("populær".equals(sorter)) {
            kokeboker = new ArrayList<>(kokeboker);
            kokeboker.sort((a, b) -> Long.compare(
                    antallSalg.getOrDefault(b.getId(), 0L),
                    antallSalg.getOrDefault(a.getId(), 0L)));
        } else if ("billigst".equals(sorter)) {
            kokeboker = new ArrayList<>(kokeboker);
            kokeboker.sort(Comparator.comparingInt(s -> s.getPris() != null ? s.getPris() : 0));
        }

        model.addAttribute("kokeboker", kokeboker);
        model.addAttribute("skapere", skapere);
        model.addAttribute("antallOppskrifter", antallOppskrifter);
        model.addAttribute("antallSalg", antallSalg);
        model.addAttribute("mine", mine);
        model.addAttribute("sorter", sorter != null ? sorter : "nyest");
        return "kokeboker";
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
