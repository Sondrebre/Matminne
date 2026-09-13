package com.example.matminne.controller;

import com.example.matminne.model.Bruker;
import com.example.matminne.model.Samling;
import com.example.matminne.model.SamlingOppskrift;
import com.example.matminne.model.Oppskrift;
import com.example.matminne.repository.*;
import com.example.matminne.service.BrukerService;
import com.example.matminne.service.SkaperService;
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
 * Godkjenning av søknader om å selge kokebøker.
 * Kun e-poster i app.admin har tilgang.
 */
@Controller
@RequestMapping("/admin")
public class AdminController {

    private static final Logger log = LoggerFactory.getLogger(AdminController.class);

    @Autowired private BrukerService brukerService;
    @Autowired private SkaperService skaperService;
    @Autowired private SamlingRepository samlingRepository;
    @Autowired private SamlingOppskriftRepository samlingOppskriftRepository;
    @Autowired private OppskriftRepository oppskriftRepository;
    @Autowired private KjopRepository kjopRepository;

    private Bruker admin(OAuth2User principal) {
        if (principal == null) return null;
        Bruker b = brukerService.finnVedEpost(principal.getAttribute("email"));
        return skaperService.erAdmin(b) ? b : null;
    }

    // ── SØKNADSKØ ─────────────────────────────────────────────────

    @GetMapping("/kokeboker")
    public String soknader(Model model, @AuthenticationPrincipal OAuth2User principal) {
        if (admin(principal) == null) return "redirect:/kokebok";

        List<Samling> koen = skaperService.soknadskoen();
        List<Samling> behandlede = samlingRepository.finnBehandlede();

        model.addAttribute("koen", koen);
        model.addAttribute("behandlede", behandlede);
        model.addAttribute("skapere", skaperePerSamling(koen, behandlede));
        model.addAttribute("antallOppskrifter", oppskriftsantall(koen, behandlede));
        model.addAttribute("plattformAndel", skaperService.getPlattformAndelProsent());
        return "admin-kokeboker";
    }

    /** Detaljert vurdering av én søknad, med oppskriftene den inneholder. */
    @GetMapping("/kokeboker/{id}")
    public String vurder(@PathVariable Long id, Model model,
                         @AuthenticationPrincipal OAuth2User principal) {
        if (admin(principal) == null) return "redirect:/kokebok";
        Samling s = samlingRepository.findById(id).orElse(null);
        if (s == null) return "redirect:/admin/kokeboker";

        List<Long> ids = samlingOppskriftRepository.findBySamlingId(id)
                .stream().map(SamlingOppskrift::getOppskriftId).collect(Collectors.toList());
        List<Oppskrift> oppskrifter = ids.isEmpty()
                ? new ArrayList<>() : oppskriftRepository.findAllById(ids);

        int andel = skaperService.getPlattformAndelProsent();
        model.addAttribute("samling", s);
        model.addAttribute("skaper", brukerService.findById(s.getBrukerId()));
        model.addAttribute("oppskrifter", oppskrifter);
        model.addAttribute("antallSalg", kjopRepository.countBySamlingId(id));
        model.addAttribute("plattformAndel", andel);
        model.addAttribute("dinAndel", (s.getPris() != null ? s.getPris() / 100 : 0) - s.skaperAndelKroner(andel));
        model.addAttribute("skaperAndel", s.skaperAndelKroner(andel));
        return "admin-vurder";
    }

    @PostMapping("/kokeboker/{id}/godkjenn")
    public String godkjenn(@PathVariable Long id, @AuthenticationPrincipal OAuth2User principal) {
        Bruker adm = admin(principal);
        if (adm == null) return "redirect:/kokebok";
        Samling s = samlingRepository.findById(id).orElse(null);
        if (s == null) return "redirect:/admin/kokeboker";

        skaperService.godkjenn(s);
        log.info("Kokebok {} («{}») godkjent av {}", id, s.getNavn(), adm.getEpost());
        return "redirect:/admin/kokeboker?godkjent=true";
    }

    @PostMapping("/kokeboker/{id}/avsla")
    public String avsla(@PathVariable Long id,
                        @RequestParam(required = false) String grunn,
                        @AuthenticationPrincipal OAuth2User principal) {
        Bruker adm = admin(principal);
        if (adm == null) return "redirect:/kokebok";
        Samling s = samlingRepository.findById(id).orElse(null);
        if (s == null) return "redirect:/admin/kokeboker";

        skaperService.avsla(s, grunn);
        log.info("Kokebok {} avslått av {}", id, adm.getEpost());
        return "redirect:/admin/kokeboker?avslatt=true";
    }

    // ── HJELPERE ──────────────────────────────────────────────────

    private Map<Long, Bruker> skaperePerSamling(List<Samling> a, List<Samling> b) {
        Map<Long, Bruker> kart = new HashMap<>();
        for (Samling s : a) leggTilSkaper(kart, s);
        for (Samling s : b) leggTilSkaper(kart, s);
        return kart;
    }

    private void leggTilSkaper(Map<Long, Bruker> kart, Samling s) {
        if (s.getBrukerId() != null && !kart.containsKey(s.getId())) {
            Bruker b = brukerService.findById(s.getBrukerId());
            if (b != null) kart.put(s.getId(), b);
        }
    }

    private Map<Long, Long> oppskriftsantall(List<Samling> a, List<Samling> b) {
        Map<Long, Long> kart = new HashMap<>();
        for (Samling s : a) kart.put(s.getId(), samlingOppskriftRepository.countBySamlingId(s.getId()));
        for (Samling s : b) kart.put(s.getId(), samlingOppskriftRepository.countBySamlingId(s.getId()));
        return kart;
    }
}
