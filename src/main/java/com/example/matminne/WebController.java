package com.example.matminne;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.data.domain.PageRequest;

import jakarta.servlet.http.HttpServletRequest;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.Collections;
import java.util.Set;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;

@Controller
public class WebController {

    @Autowired private VennskapRepository vennskapRepository;
    @Autowired private OppskriftRepository repository;
    @Autowired private BrukerService brukerService;
    @Autowired private AiOppskriftService aiOppskriftService;
    @Autowired private LikeRepository likeRepository;
    @Autowired private KommentarRepository kommentarRepository;
    @Autowired private VarselRepository varselRepository;
    @Autowired private SamlingRepository samlingRepository;
    @Autowired private SamlingOppskriftRepository samlingOppskriftRepository;
    @Autowired private RatingRepository ratingRepository;
    @Autowired private HandelListeRepository handelListeRepository;
    @Autowired private UkesmenyRepository ukesmenyRepository;
    @Autowired private UtfordringRepository utfordringRepository;

    private static final String DEFAULT_IMAGE = "https://images.unsplash.com/photo-1495195129352-aeb325a55b65?auto=format&fit=crop&w=1600&q=90";

    // ── GLOBAL MODEL ──────────────────────────────────────────────
    @ModelAttribute
    public void leggTilGlobalInfo(Model model, @AuthenticationPrincipal OAuth2User principal) {
        if (principal != null) {
            String epost = principal.getAttribute("email");
            model.addAttribute("brukernavn", principal.getAttribute("name"));
            model.addAttribute("profilBilde", principal.getAttribute("picture"));
            model.addAttribute("brukerEpost", epost);
            model.addAttribute("innlogget", true);
            Bruker meg = brukerService.finnVedEpost(epost);
            if (meg != null) {
                model.addAttribute("ulesVarsler",
                    varselRepository.countByMottakerBrukerIdAndLestFalse(meg.getId()));
                model.addAttribute("harAbonnement", meg.isHarAbonnement());
            } else {
                model.addAttribute("harAbonnement", false);
            }
        } else {
            model.addAttribute("innlogget", false);
            model.addAttribute("ulesVarsler", 0L);
        }
    }

    // ── HOME ──────────────────────────────────────────────────────
    @GetMapping("/")
    public String home(@AuthenticationPrincipal OAuth2User principal) {
        if (principal != null) {
            String epost = principal.getAttribute("email");
            Bruker meg = brukerService.finnVedEpost(epost);
            if (meg == null) {
                meg = new Bruker();
                meg.setEpost(epost);
                meg.setFulltNavn(principal.getAttribute("name"));
                brukerService.lagreBruker(meg);
            }
            return "redirect:/kokebok";
        }
        return "home";
    }

    // ── KOKEBOK ───────────────────────────────────────────────────
    @GetMapping("/kokebok")
    public String visKokebok(Model model, @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";
        Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
        if (meg == null) {
            model.addAttribute("oppskrifter", Collections.emptyList());
            model.addAttribute("antall", 0);
            return "kokebok";
        }
        List<Oppskrift> mineOppskrifter = repository.findByBrukerId(meg.getId());
        Collections.reverse(mineOppskrifter);
        model.addAttribute("oppskrifter", mineOppskrifter);
        model.addAttribute("antall", mineOppskrifter.size());
        return "kokebok";
    }

    // ── INSTALLER APP ─────────────────────────────────────────────
    @GetMapping("/installer-app")
    public String visInstallerApp() {
        return "installer-app";
    }

    // ── AI GENERER ────────────────────────────────────────────────
    @PostMapping("/generer")
    public String genererFraUrl(@RequestParam String url, Model model,
                                 @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";
        Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
        if (meg == null || !meg.isHarAbonnement()) {
            return "redirect:/abonnement?krever=ai";
        }
        try {
            Oppskrift aiOppskrift = aiOppskriftService.hentOgStrukturer(url);
            if (aiOppskrift != null) {
                model.addAttribute("oppskrift", aiOppskrift);
                return "ny-oppskrift";
            }
        } catch (Exception e) {
            model.addAttribute("feil", "AI-tjenesten er utilgjengelig.");
        }
        return "ny-oppskrift";
    }

    // ── AI INGREDIENS SUBSTITUTT ──────────────────────────────────
    @PostMapping("/api/substitutt")
    @ResponseBody
    public ResponseEntity<Map<String, String>> genererSubstitutt(
            @RequestParam String ingrediens,
            @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
        if (meg == null || !meg.isHarAbonnement()) {
            Map<String, String> err = new HashMap<>();
            err.put("feil", "KREVER_ABONNEMENT");
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(err);
        }
        String svar = aiOppskriftService.genererSubstitutt(ingrediens.trim());
        Map<String, String> result = new HashMap<>();
        result.put("svar", svar);
        return ResponseEntity.ok(result);
    }

    // ── OPPDAG ────────────────────────────────────────────────────
    @GetMapping("/oppdag")
    public String visOppdag(Model model,
                             @RequestParam(required = false) String q,
                             @RequestParam(required = false) String kategori,
                             @RequestParam(required = false) String sorter) {
        List<Oppskrift> oppskrifter;
        if (q != null && !q.isBlank()) {
            oppskrifter = repository.findByErOffentligTrueAndTittelContainingIgnoreCaseOrderByIdDesc(q);
        } else if (kategori != null && !kategori.isBlank()) {
            oppskrifter = repository.findByErOffentligTrueAndKategoriOrderByIdDesc(kategori);
        } else {
            oppskrifter = repository.findByErOffentligTrueOrderByIdDesc();
        }
        if ("eldst".equals(sorter)) Collections.reverse(oppskrifter);

        // Trending: topp 6 mest likte offentlige oppskrifter
        boolean visTrending = (q == null || q.isBlank()) && (kategori == null || kategori.isBlank());
        if (visTrending) {
            List<Long> trendingIds = likeRepository.findTopLikedOppskriftIds(PageRequest.of(0, 6));
            List<Oppskrift> trending = trendingIds.stream()
                .map(id -> repository.findById(id).orElse(null))
                .filter(o -> o != null && o.isErOffentlig())
                .collect(Collectors.toList());
            model.addAttribute("trending", trending);
        } else {
            model.addAttribute("trending", Collections.emptyList());
        }

        model.addAttribute("oppskrifter", oppskrifter);
        model.addAttribute("aktivKategori", kategori);
        model.addAttribute("sokeTekst", q);
        model.addAttribute("sortering", sorter);
        return "oppdag";
    }

    // ── VENNEFEED ─────────────────────────────────────────────────
    @GetMapping("/vennefeed")
    public String visVenneFeed(Model model, @AuthenticationPrincipal OAuth2User principal,
                                @RequestParam(required = false) String sok) {
        if (principal == null) return "redirect:/";
        model.addAttribute("oppskrifter", Collections.emptyList());
        model.addAttribute("sokeTekst", sok);
        Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
        if (meg != null) {
            List<Long> vennerIds = vennskapRepository.findByBrukerId(meg.getId())
                    .stream().map(Vennskap::getVennId).collect(Collectors.toList());
            if (!vennerIds.isEmpty()) {
                model.addAttribute("oppskrifter",
                    repository.findByBrukerIdInAndErOffentligTrueOrderByIdDesc(vennerIds));
            }
            // Søk etter personer
            if (sok != null && !sok.isBlank()) {
                List<Bruker> sokResultater = brukerService.sokEtterNavn(sok);
                model.addAttribute("sokResultater", sokResultater);
                Set<Long> fulgte = vennerIds.stream().collect(Collectors.toSet());
                model.addAttribute("fulgteBrukerIds", fulgte);
            }
        }
        return "vennefeed";
    }

    // ── PROFIL ────────────────────────────────────────────────────
    @GetMapping("/profil/{epost}")
    public String visProfil(@PathVariable String epost, Model model,
                             @AuthenticationPrincipal OAuth2User principal) {
        Bruker profilBruker = brukerService.finnVedEpost(epost);
        if (profilBruker == null) return "redirect:/oppdag";

        List<Oppskrift> offentlige = repository.findByBrukerId(profilBruker.getId())
                .stream().filter(Oppskrift::isErOffentlig).collect(Collectors.toList());

        boolean følgerAllerede = false;
        if (principal != null) {
            Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
            if (meg != null)
                følgerAllerede = vennskapRepository.findByBrukerIdAndVennId(meg.getId(), profilBruker.getId()) != null;
        }

        model.addAttribute("erEgenProfil", principal != null && principal.getAttribute("email").equals(epost));
        model.addAttribute("følgerAllerede", følgerAllerede);
        model.addAttribute("profilNavn", profilBruker.getFulltNavn());
        model.addAttribute("profilEpost", epost);
        model.addAttribute("profilBrukerBilde", profilBruker.getBildeUrl());
        model.addAttribute("oppskrifter", offentlige);
        model.addAttribute("antallFølgere", vennskapRepository.countByVennId(profilBruker.getId()));
        return "profil";
    }

    // ── FØLG / SLUTT Å FØLGE ──────────────────────────────────────
    @PostMapping("/følg/{vennEpost}")
    public String følgBruker(@PathVariable String vennEpost,
                              @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";
        Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
        Bruker denAndre = brukerService.finnVedEpost(vennEpost);
        if (meg != null && denAndre != null && !meg.getId().equals(denAndre.getId())) {
            if (!vennskapRepository.existsByBrukerIdAndVennId(meg.getId(), denAndre.getId())) {
                Vennskap v = new Vennskap();
                v.setBrukerId(meg.getId());
                v.setVennId(denAndre.getId());
                vennskapRepository.save(v);
                lagrVarsel(denAndre.getId(),
                    meg.getFulltNavn() + " begynte å følge deg!",
                    "/profil/" + principal.getAttribute("email"));
            }
        }
        return "redirect:/profil/" + vennEpost;
    }

    @PostMapping("/slutt-å-følge/{vennEpost}")
    public String sluttÅFølge(@PathVariable String vennEpost,
                               @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";
        Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
        Bruker denAndre = brukerService.finnVedEpost(vennEpost);
        if (meg != null && denAndre != null) {
            Vennskap v = vennskapRepository.findByBrukerIdAndVennId(meg.getId(), denAndre.getId());
            if (v != null) vennskapRepository.delete(v);
        }
        return "redirect:/profil/" + vennEpost;
    }

    // ── LAGRE / OPPDATER OPPSKRIFT ────────────────────────────────
    @PostMapping("/lagre")
    public String lagreOppskrift(@ModelAttribute Oppskrift oppskrift,
                                  @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";
        Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
        if (meg != null) {
            oppskrift.setBrukerId(meg.getId());
            oppskrift.setBrukerEpost(meg.getEpost());
            oppskrift.setBrukerNavn(principal.getAttribute("name"));
            if (oppskrift.getBildeUrl() == null || oppskrift.getBildeUrl().isEmpty())
                oppskrift.setBildeUrl(DEFAULT_IMAGE);
            repository.save(oppskrift);
        }
        return "redirect:/kokebok";
    }

    @PostMapping("/oppdater/{id}")
    public String oppdaterOppskrift(@PathVariable Long id,
                                     @ModelAttribute Oppskrift data,
                                     @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";
        Oppskrift eks = repository.findById(id).orElse(null);
        if (eks != null && eks.getBrukerEpost().equals(principal.getAttribute("email"))) {
            eks.setTittel(data.getTittel());
            eks.setIngredienser(data.getIngredienser());
            eks.setFremgangsmate(data.getFremgangsmate());
            eks.setErOffentlig(data.isErOffentlig());
            eks.setBildeUrl(data.getBildeUrl());
            eks.setKategori(data.getKategori());
            eks.setTilberedningstid(data.getTilberedningstid());
            eks.setPorsjoner(data.getPorsjoner());
            eks.setVanskelighet(data.getVanskelighet());
            repository.save(eks);
        }
        return "redirect:/detaljer/" + id;
    }

    // ── KOPIER ────────────────────────────────────────────────────
    @PostMapping("/kopier/{id}")
    public String kopierOppskrift(@PathVariable Long id,
                                   @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";
        Oppskrift original = repository.findById(id).orElse(null);
        if (original == null) return "redirect:/kokebok";
        Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
        if (meg != null) {
            Oppskrift kopi = new Oppskrift();
            kopi.setTittel(original.getTittel() + " (Kopi)");
            kopi.setIngredienser(original.getIngredienser());
            kopi.setFremgangsmate(original.getFremgangsmate());
            kopi.setBildeUrl(original.getBildeUrl());
            kopi.setKategori(original.getKategori());
            kopi.setTilberedningstid(original.getTilberedningstid());
            kopi.setPorsjoner(original.getPorsjoner());
            kopi.setVanskelighet(original.getVanskelighet());
            kopi.setBrukerId(meg.getId());
            kopi.setBrukerEpost(meg.getEpost());
            kopi.setBrukerNavn(meg.getFulltNavn());
            kopi.setErOffentlig(false);
            repository.save(kopi);
        }
        return "redirect:/kokebok";
    }

    // ── SLETT ─────────────────────────────────────────────────────
    @PostMapping("/slett/{id}")
    public String slettOppskrift(@PathVariable Long id,
                                  @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";
        Oppskrift o = repository.findById(id).orElse(null);
        if (o != null && o.getBrukerEpost().equals(principal.getAttribute("email")))
            repository.delete(o);
        return "redirect:/kokebok";
    }

    // ── DETALJER ──────────────────────────────────────────────────
    @GetMapping("/detaljer/{id}")
    public String visDetaljer(@PathVariable Long id, Model model,
                               @AuthenticationPrincipal OAuth2User principal) {
        Oppskrift o = repository.findById(id).orElse(null);
        if (o == null) return "detaljer";

        String epost = principal != null ? (String) principal.getAttribute("email") : null;
        boolean erEier = epost != null && epost.equals(o.getBrukerEpost());
        if (!o.isErOffentlig() && !erEier) return "redirect:/oppdag";

        long likeAntall = likeRepository.countByOppskriftId(id);
        boolean erLikt = false;
        Long innloggetBrukerId = null;
        List<Samling> mineSamlinger = Collections.emptyList();

        if (principal != null) {
            Bruker meg = brukerService.finnVedEpost(epost);
            if (meg != null) {
                innloggetBrukerId = meg.getId();
                erLikt = likeRepository.existsByBrukerIdAndOppskriftId(meg.getId(), id);
                mineSamlinger = samlingRepository.findByBrukerIdOrderByOpprettetDesc(meg.getId());
            }
        }

        // Rating
        double snittRating = ratingRepository.snitRatingForOppskrift(id);
        int minRating = 0;
        if (innloggetBrukerId != null) {
            Rating eksRating = ratingRepository.findByBrukerIdAndOppskriftId(innloggetBrukerId, id);
            if (eksRating != null) minRating = eksRating.getVerdi();
        }

        // Kommentarer gruppert
        List<Kommentar> alleKommentarer = kommentarRepository.findByOppskriftIdOrderByOpprettetAsc(id);

        model.addAttribute("o", o);
        model.addAttribute("erEier", erEier);
        model.addAttribute("likeAntall", likeAntall);
        model.addAttribute("erLikt", erLikt);
        model.addAttribute("kommentarer", alleKommentarer);
        model.addAttribute("innloggetBrukerId", innloggetBrukerId);
        model.addAttribute("mineSamlinger", mineSamlinger);
        model.addAttribute("snittRating", snittRating);
        model.addAttribute("minRating", minRating);
        model.addAttribute("antallRatinger", ratingRepository.countByOppskriftId(id));
        return "detaljer";
    }

    // ── REDIGER ───────────────────────────────────────────────────
    @GetMapping("/ny-oppskrift")
    public String visNyOppskriftForm(Model model) {
        model.addAttribute("oppskrift", new Oppskrift());
        return "ny-oppskrift";
    }

    @GetMapping("/rediger/{id}")
    public String visRedigerForm(@PathVariable Long id, Model model,
                                  @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";
        Oppskrift o = repository.findById(id).orElse(null);
        if (o == null || !o.getBrukerEpost().equals(principal.getAttribute("email")))
            return "redirect:/kokebok";
        model.addAttribute("o", o);
        return "rediger";
    }

    // ── LIKES ─────────────────────────────────────────────────────
    @PostMapping("/lik/{oppskriftId}")
    public String toggleLik(@PathVariable Long oppskriftId,
                             @AuthenticationPrincipal OAuth2User principal,
                             HttpServletRequest request) {
        if (principal == null) return "redirect:/";
        Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
        if (meg != null) {
            Like existing = likeRepository.findByBrukerIdAndOppskriftId(meg.getId(), oppskriftId);
            if (existing != null) {
                likeRepository.delete(existing);
            } else {
                likeRepository.save(new Like(meg.getId(), oppskriftId));
                Oppskrift o = repository.findById(oppskriftId).orElse(null);
                if (o != null && !o.getBrukerId().equals(meg.getId())) {
                    lagrVarsel(o.getBrukerId(),
                        meg.getFulltNavn() + " likte oppskriften «" + o.getTittel() + "»",
                        "/detaljer/" + oppskriftId);
                }
            }
        }
        String referer = request.getHeader("Referer");
        return "redirect:" + (referer != null ? referer : "/detaljer/" + oppskriftId);
    }

    // ── KOMMENTARER ───────────────────────────────────────────────
    @PostMapping("/kommentar/{oppskriftId}")
    public String leggTilKommentar(@PathVariable Long oppskriftId,
                                    @RequestParam String tekst,
                                    @RequestParam(required = false) Long foreldreId,
                                    @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";
        Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
        if (meg != null && tekst != null && !tekst.isBlank()) {
            Kommentar k = new Kommentar();
            k.setOppskriftId(oppskriftId);
            k.setBrukerId(meg.getId());
            k.setBrukerNavn(meg.getFulltNavn());
            k.setTekst(tekst.trim());
            k.setForeldreId(foreldreId);
            kommentarRepository.save(k);
            Oppskrift o = repository.findById(oppskriftId).orElse(null);
            if (o != null && !o.getBrukerId().equals(meg.getId())) {
                lagrVarsel(o.getBrukerId(),
                    meg.getFulltNavn() + " kommenterte på «" + o.getTittel() + "»",
                    "/detaljer/" + oppskriftId);
            }
            // Varsle original kommentarskriver ved svar
            if (foreldreId != null) {
                Kommentar forelder = kommentarRepository.findById(foreldreId).orElse(null);
                if (forelder != null && !forelder.getBrukerId().equals(meg.getId())) {
                    lagrVarsel(forelder.getBrukerId(),
                        meg.getFulltNavn() + " svarte på kommentaren din",
                        "/detaljer/" + oppskriftId);
                }
            }
        }
        return "redirect:/detaljer/" + oppskriftId;
    }

    @PostMapping("/slett-kommentar/{id}")
    public String slettKommentar(@PathVariable Long id,
                                  @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";
        Kommentar k = kommentarRepository.findById(id).orElse(null);
        if (k == null) return "redirect:/kokebok";
        Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
        if (meg != null && k.getBrukerId().equals(meg.getId()))
            kommentarRepository.delete(k);
        return "redirect:/detaljer/" + k.getOppskriftId();
    }

    // ── VARSLER ───────────────────────────────────────────────────
    @GetMapping("/varsler")
    public String visVarsler(Model model, @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";
        model.addAttribute("varsler", Collections.emptyList());
        Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
        if (meg != null) {
            List<Varsel> varsler = varselRepository.findByMottakerBrukerIdOrderByOpprettetDesc(meg.getId());
            varsler.forEach(v -> { v.setLest(true); varselRepository.save(v); });
            model.addAttribute("varsler", varsler);
        }
        return "varsler";
    }

    // ── SAMLINGER ─────────────────────────────────────────────────
    @GetMapping("/samlinger")
    public String visSamlinger(Model model, @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";
        model.addAttribute("samlinger", Collections.emptyList());
        model.addAttribute("antallPerSamling", new HashMap<Long, Long>());
        model.addAttribute("forsideBilder", new HashMap<Long, String>());
        Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
        if (meg != null) {
            List<Samling> samlinger = samlingRepository.findByBrukerIdOrderByOpprettetDesc(meg.getId());
            Map<Long, Long> antall = new HashMap<>();
            Map<Long, String> bilder = new HashMap<>();
            samlinger.forEach(s -> {
                antall.put(s.getId(), samlingOppskriftRepository.countBySamlingId(s.getId()));
                List<SamlingOppskrift> items = samlingOppskriftRepository.findBySamlingId(s.getId());
                if (!items.isEmpty()) {
                    repository.findById(items.get(0).getOppskriftId()).ifPresent(o -> {
                        if (o.getBildeUrl() != null && !o.getBildeUrl().isEmpty())
                            bilder.put(s.getId(), o.getBildeUrl());
                    });
                }
            });
            model.addAttribute("samlinger", samlinger);
            model.addAttribute("antallPerSamling", antall);
            model.addAttribute("forsideBilder", bilder);
        }
        return "samlinger";
    }

    @PostMapping("/samlinger/ny")
    public String nyaSamling(@RequestParam String navn,
                              @RequestParam(required = false) String beskrivelse,
                              @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";
        Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
        if (meg != null && navn != null && !navn.isBlank()) {
            Samling s = new Samling();
            s.setBrukerId(meg.getId());
            s.setNavn(navn.trim());
            s.setBeskrivelse(beskrivelse != null ? beskrivelse.trim() : "");
            samlingRepository.save(s);
        }
        return "redirect:/samlinger";
    }

    @GetMapping("/samlinger/{id}")
    public String visSamlingDetaljer(@PathVariable Long id, Model model,
                                      @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";
        Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
        Samling s = samlingRepository.findById(id).orElse(null);
        if (s == null || !s.getBrukerId().equals(meg.getId()))
            return "redirect:/samlinger";

        List<Long> oppskriftIds = samlingOppskriftRepository.findBySamlingId(id)
                .stream().map(SamlingOppskrift::getOppskriftId).collect(Collectors.toList());
        List<Oppskrift> oppskrifter = oppskriftIds.stream()
                .map(oid -> repository.findById(oid).orElse(null))
                .filter(o -> o != null)
                .collect(Collectors.toList());

        model.addAttribute("samling", s);
        model.addAttribute("oppskrifter", oppskrifter);
        return "samling-detaljer";
    }

    @PostMapping("/samlinger/{id}/legg-til/{oppskriftId}")
    public String leggTilISamling(@PathVariable Long id, @PathVariable Long oppskriftId,
                                   @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";
        Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
        Samling s = samlingRepository.findById(id).orElse(null);
        if (meg != null && s != null && s.getBrukerId().equals(meg.getId())) {
            if (!samlingOppskriftRepository.existsBySamlingIdAndOppskriftId(id, oppskriftId))
                samlingOppskriftRepository.save(new SamlingOppskrift(id, oppskriftId));
        }
        return "redirect:/detaljer/" + oppskriftId;
    }

    @PostMapping("/samlinger/{id}/fjern/{oppskriftId}")
    public String fjernFraSamling(@PathVariable Long id, @PathVariable Long oppskriftId,
                                   @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";
        Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
        Samling s = samlingRepository.findById(id).orElse(null);
        if (meg != null && s != null && s.getBrukerId().equals(meg.getId())) {
            samlingOppskriftRepository.findBySamlingIdAndOppskriftId(id, oppskriftId)
                    .ifPresent(samlingOppskriftRepository::delete);
        }
        return "redirect:/samlinger/" + id;
    }

    @PostMapping("/samlinger/slett/{id}")
    public String slettSamling(@PathVariable Long id,
                                @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";
        Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
        Samling s = samlingRepository.findById(id).orElse(null);
        if (meg != null && s != null && s.getBrukerId().equals(meg.getId())) {
            samlingOppskriftRepository.findBySamlingId(id).forEach(samlingOppskriftRepository::delete);
            samlingRepository.delete(s);
        }
        return "redirect:/samlinger";
    }

    // ── INNSTILLINGER ─────────────────────────────────────────────
    @GetMapping("/innstillinger")
    public String visInnstillinger(@AuthenticationPrincipal OAuth2User principal, Model model) {
        if (principal == null) return "redirect:/";
        model.addAttribute("navn", principal.getAttribute("name"));
        model.addAttribute("epost", principal.getAttribute("email"));
        model.addAttribute("bilde", principal.getAttribute("picture"));
        return "innstillinger";
    }

    @PostMapping("/oppdater-profilbilde")
    public String oppdaterProfilbilde(@RequestParam String nyBildeUrl,
                                       @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";
        Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
        if (meg != null && nyBildeUrl != null && !nyBildeUrl.isBlank()) {
            meg.setBildeUrl(nyBildeUrl);
            brukerService.lagreBruker(meg);
        }
        return "redirect:/profil/" + principal.getAttribute("email");
    }

    // ── SØKETREFF PERSONER ────────────────────────────────────────
    @GetMapping("/sok/personer")
    public String visPersonSok(@RequestParam(required = false) String navn, Model model,
                                @AuthenticationPrincipal OAuth2User principal) {
        if (navn != null && !navn.isBlank()) {
            model.addAttribute("brukere", brukerService.sokEtterNavn(navn));
            model.addAttribute("sokeord", navn);
        }
        if (principal != null) {
            Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
            if (meg != null) {
                Set<Long> fulgte = vennskapRepository.findByBrukerId(meg.getId())
                        .stream().map(Vennskap::getVennId).collect(Collectors.toSet());
                model.addAttribute("fulgteBrukerIds", fulgte);
                model.addAttribute("innloggetBrukerId", meg.getId());
            }
        }
        return "bruker-sok";
    }

    // ── PDF ───────────────────────────────────────────────────────
    @PostMapping("/pdf")
    public ResponseEntity<byte[]> genererPdf(@RequestParam Long id) {
        try {
            Oppskrift o = repository.findById(id).orElse(null);
            if (o == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();

            String ingSafe  = o.getIngredienser()  != null ? o.getIngredienser().replace("\n", "<br>")  : "";
            String stegSafe = o.getFremgangsmate()  != null ? o.getFremgangsmate().replace("\n", "<br>") : "";
            String bildeSrc = (o.getBildeUrl() != null && !o.getBildeUrl().isEmpty()) ? o.getBildeUrl() : "";
            String bildeHtml = bildeSrc.isEmpty() ? "" :
                "<div style='width:100%;height:220px;overflow:hidden;border-radius:16px;margin-bottom:32px;'>" +
                "<img src='" + bildeSrc + "' style='width:100%;height:100%;object-fit:cover;'/></div>";

            String metaHtml = "";
            if (o.getTilberedningstid() != null) metaHtml += "<span style='margin-right:16px;'>⏱ " + o.getTilberedningstid() + " min</span>";
            if (o.getPorsjoner()        != null) metaHtml += "<span style='margin-right:16px;'>🍽 " + o.getPorsjoner() + " porsjoner</span>";
            if (o.getVanskelighet()     != null) metaHtml += "<span>⭐ " + o.getVanskelighet() + "</span>";

            String html =
                "<!DOCTYPE html><html><head><meta charset='UTF-8'/><style>" +
                "  @page { size: A4; margin: 0; }" +
                "  * { box-sizing: border-box; margin: 0; padding: 0; }" +
                "  body { font-family: 'Helvetica Neue', Arial, sans-serif; background: #fff; color: #1e293b; }" +
                "  .header { background: #0f172a; padding: 40px 50px 32px; }" +
                "  .header-tag { font-size: 10px; font-weight: 700; text-transform: uppercase; letter-spacing: 3px; color: #2ecc71; margin-bottom: 10px; }" +
                "  .header h1 { font-size: 32px; font-weight: 900; color: #fff; letter-spacing: -1px; line-height: 1.1; }" +
                "  .header-meta { margin-top: 8px; font-size: 11px; color: rgba(255,255,255,0.4); }" +
                "  .meta-badges { margin-top: 14px; font-size: 12px; color: rgba(255,255,255,0.65); }" +
                "  .body { padding: 36px 50px 50px; }" +
                "  .section { margin-bottom: 32px; }" +
                "  .section-title { font-size: 10px; font-weight: 800; text-transform: uppercase; letter-spacing: 2.5px; color: #2ecc71; margin-bottom: 14px; padding-bottom: 8px; border-bottom: 2px solid #f1f5f9; }" +
                "  .section-content { font-size: 13px; line-height: 1.85; color: #475569; }" +
                "  .footer { position: fixed; bottom: 0; left: 0; right: 0; background: #f8fafc; border-top: 1px solid #f1f5f9; padding: 10px 50px; display: flex; justify-content: space-between; font-size: 10px; color: #94a3b8; }" +
                "</style></head><body>" +
                "<div class='header'>" +
                "  <div class='header-tag'>MatMinne – Oppskrift</div>" +
                "  <h1>" + o.getTittel() + "</h1>" +
                "  <div class='header-meta'>Av " + (o.getBrukerNavn() != null ? o.getBrukerNavn() : "ukjent") + "</div>" +
                (metaHtml.isEmpty() ? "" : "<div class='meta-badges'>" + metaHtml + "</div>") +
                "</div>" +
                "<div class='body'>" +
                bildeHtml +
                (ingSafe.isEmpty()  ? "" : "<div class='section'><div class='section-title'>Ingredienser</div><div class='section-content'>" + ingSafe + "</div></div>") +
                (stegSafe.isEmpty() ? "" : "<div class='section'><div class='section-title'>Fremgangsmåte</div><div class='section-content'>" + stegSafe + "</div></div>") +
                "</div>" +
                "<div class='footer'><span>matminne.no</span><span>" + o.getTittel() + "</span></div>" +
                "</body></html>";

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.withHtmlContent(html, null);
            builder.toStream(baos);
            builder.run();

            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"oppskrift.pdf\"")
                    .contentType(MediaType.APPLICATION_PDF)
                    .body(baos.toByteArray());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().build();
        }
    }

    // ── RATING ────────────────────────────────────────────────────
    @PostMapping("/rating/{oppskriftId}")
    public String giRating(@PathVariable Long oppskriftId,
                            @RequestParam int verdi,
                            @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";
        Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
        if (meg != null && verdi >= 1 && verdi <= 5) {
            Rating existing = ratingRepository.findByBrukerIdAndOppskriftId(meg.getId(), oppskriftId);
            if (existing != null) {
                existing.setVerdi(verdi);
                ratingRepository.save(existing);
            } else {
                Rating r = new Rating();
                r.setBrukerId(meg.getId());
                r.setOppskriftId(oppskriftId);
                r.setVerdi(verdi);
                ratingRepository.save(r);
            }
        }
        return "redirect:/detaljer/" + oppskriftId;
    }

    // ── HANDLELISTE ───────────────────────────────────────────────
    @GetMapping("/handleliste")
    public String visHandelListe(Model model, @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";
        Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
        List<HandelListeItem> items = Collections.emptyList();
        if (meg != null) items = handelListeRepository.findByBrukerIdOrderByFerdigAscOpprettetDesc(meg.getId());
        model.addAttribute("items", items);
        model.addAttribute("antall", items.size());
        model.addAttribute("antallFerdig", items.stream().filter(HandelListeItem::isFerdig).count());
        return "handleliste";
    }

    @PostMapping("/handleliste/legg-til")
    public String leggTilHandelVare(@RequestParam String tekst,
                                     @RequestParam(required = false) String kategori,
                                     @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";
        Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
        if (meg != null && tekst != null && !tekst.isBlank()) {
            HandelListeItem item = new HandelListeItem();
            item.setBrukerId(meg.getId());
            item.setTekst(tekst.trim());
            item.setKategori(kategori);
            handelListeRepository.save(item);
        }
        return "redirect:/handleliste";
    }

    @PostMapping("/handleliste/fra-oppskrift/{id}")
    public String leggTilFraOppskrift(@PathVariable Long id,
                                       @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";
        Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
        Oppskrift o = repository.findById(id).orElse(null);
        if (meg != null && o != null && o.getIngredienser() != null) {
            for (String linje : o.getIngredienser().split("\n")) {
                String trimmet = linje.trim();
                if (!trimmet.isEmpty()) {
                    HandelListeItem item = new HandelListeItem();
                    item.setBrukerId(meg.getId());
                    item.setTekst(trimmet);
                    handelListeRepository.save(item);
                }
            }
        }
        return "redirect:/handleliste";
    }

    @PostMapping("/handleliste/toggle/{id}")
    public String toggleHandelVare(@PathVariable Long id,
                                    @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";
        HandelListeItem item = handelListeRepository.findById(id).orElse(null);
        if (item != null) {
            item.setFerdig(!item.isFerdig());
            handelListeRepository.save(item);
        }
        return "redirect:/handleliste";
    }

    @PostMapping("/handleliste/slett/{id}")
    public String slettHandelVare(@PathVariable Long id,
                                   @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";
        handelListeRepository.deleteById(id);
        return "redirect:/handleliste";
    }

    @PostMapping("/handleliste/rydd")
    public String ryddHandelListe(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";
        Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
        if (meg != null) handelListeRepository.deleteByBrukerIdAndFerdigTrue(meg.getId());
        return "redirect:/handleliste";
    }

    // ── AI UKESMENY ───────────────────────────────────────────────
    @GetMapping("/ukesmeny/ai")
    public String visAiUkesmeny(Model model, @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";
        Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
        if (meg == null || !meg.isHarAbonnement()) return "redirect:/abonnement?krever=ai";
        return "ukesmeny-ai";
    }

    @PostMapping("/ukesmeny/ai/generer")
    public String genererAiUkesmeny(
            @RequestParam(defaultValue = "3") int kjottMiddager,
            @RequestParam(defaultValue = "") String allergier,
            @RequestParam(defaultValue = "2") int porsjoner,
            @RequestParam(defaultValue = "") String ekstraOnsker,
            @AuthenticationPrincipal OAuth2User principal) {

        if (principal == null) return "redirect:/";
        Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
        if (meg == null || !meg.isHarAbonnement()) return "redirect:/abonnement?krever=ai";

        String aiSvar = aiOppskriftService.genererUkesmeny(
                Math.max(0, Math.min(7, kjottMiddager)),
                allergier, porsjoner, ekstraOnsker);

        if (aiSvar.startsWith("FEIL") || aiSvar.startsWith("RATE_LIMIT")) {
            return "redirect:/ukesmeny?feil=ai";
        }

        // Slett eksisterende ukesmeny
        List<UkesmenyItem> eksisterende = ukesmenyRepository.findByBrukerId(meg.getId());
        ukesmenyRepository.deleteAll(eksisterende);

        // Parse JSON-svaret fra Claude
        // Nøkler bruker ASCII (lordag/sondag) for å unngå encoding-problemer
        String[][] dagPar = {
            {"mandag","Mandag"},{"tirsdag","Tirsdag"},{"onsdag","Onsdag"},
            {"torsdag","Torsdag"},{"fredag","Fredag"},{"lordag","Lørdag"},{"sondag","Søndag"}
        };
        String[][] maltidPar = {{"frokost","Frokost"},{"lunsj","Lunsj"},{"middag","Middag"}};

        // Trekk ut JSON fra eventuell markdown-wrapper
        String jsonSvar = aiSvar.replaceAll("(?s)```json\\s*", "").replaceAll("```\\s*", "").trim();
        int start = jsonSvar.indexOf('{');
        int end = jsonSvar.lastIndexOf('}');
        if (start >= 0 && end > start) jsonSvar = jsonSvar.substring(start, end + 1);

        int lagret = 0;
        try {
            ObjectMapper mapper = new ObjectMapper();
            @SuppressWarnings("unchecked")
            Map<String, Map<String, String>> meny = mapper.readValue(jsonSvar, Map.class);

            for (String[] dp : dagPar) {
                Map<String, String> dagMeny = meny.get(dp[0]);
                if (dagMeny == null) continue;
                for (String[] mp : maltidPar) {
                    String tittel = dagMeny.get(mp[0]);
                    if (tittel != null && !tittel.isBlank()) {
                        UkesmenyItem item = new UkesmenyItem();
                        item.setBrukerId(meg.getId());
                        item.setDag(dp[1]);
                        item.setMaltid(mp[1]);
                        item.setOppskriftTittel(tittel.trim());
                        ukesmenyRepository.save(item);
                        lagret++;
                    }
                }
            }
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(WebController.class)
                .error("JSON-parsing av AI-ukesmeny feilet: {}. Svar:\n{}", e.getMessage(), aiSvar);
        }
        org.slf4j.LoggerFactory.getLogger(WebController.class)
            .info("AI-ukesmeny: lagret {} måltider", lagret);
        return "redirect:/ukesmeny?ai=true";
    }

    // ── UKESMENY ──────────────────────────────────────────────────
    @GetMapping("/ukesmeny")
    public String visUkesmeny(Model model, @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";
        Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
        List<UkesmenyItem> items = Collections.emptyList();
        if (meg != null) items = ukesmenyRepository.findByBrukerId(meg.getId());

        List<String> dager = List.of("Mandag", "Tirsdag", "Onsdag", "Torsdag", "Fredag", "Lørdag", "Søndag");

        // Bygg en plan-map: dag -> maltid -> item
        Map<String, Map<String, UkesmenyItem>> plan = new HashMap<>();
        for (String dag : dager) {
            Map<String, UkesmenyItem> m = new HashMap<>();
            m.put("Frokost", null); m.put("Lunsj", null); m.put("Middag", null);
            plan.put(dag, m);
        }
        for (UkesmenyItem item : items) {
            if (plan.containsKey(item.getDag()))
                plan.get(item.getDag()).put(item.getMaltid(), item);
        }

        // dagPlaner: liste med {dag, frokost, lunsj, middag}
        List<Map<String, Object>> dagPlaner = new ArrayList<>();
        for (String dag : dager) {
            Map<String, Object> dp = new HashMap<>();
            dp.put("dag", dag);
            dp.put("frokost", plan.get(dag).get("Frokost"));
            dp.put("lunsj",   plan.get(dag).get("Lunsj"));
            dp.put("middag",  plan.get(dag).get("Middag"));
            dagPlaner.add(dp);
        }

        model.addAttribute("dager", dager);
        model.addAttribute("dagPlaner", dagPlaner);
        return "ukesmeny";
    }

    @PostMapping("/ukesmeny/legg-til")
    public String leggTilUkesmeny(@RequestParam String dag,
                                   @RequestParam String maltid,
                                   @RequestParam String tittel,
                                   @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";
        if (tittel == null || tittel.isBlank()) return "redirect:/ukesmeny";
        Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
        if (meg != null) {
            ukesmenyRepository.deleteByBrukerIdAndDagAndMaltid(meg.getId(), dag, maltid);
            UkesmenyItem item = new UkesmenyItem();
            item.setBrukerId(meg.getId());
            item.setDag(dag);
            item.setMaltid(maltid);
            item.setOppskriftTittel(tittel.trim());
            ukesmenyRepository.save(item);
        }
        return "redirect:/ukesmeny";
    }

    @PostMapping("/ukesmeny/fjern/{id}")
    public String fjernFraUkesmeny(@PathVariable Long id,
                                    @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";
        ukesmenyRepository.deleteById(id);
        return "redirect:/ukesmeny";
    }

    @PostMapping("/ukesmeny/til-handleliste")
    public String ukesmenyTilHandelListe(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";
        Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
        if (meg == null) return "redirect:/handleliste";

        List<UkesmenyItem> items = ukesmenyRepository.findByBrukerId(meg.getId());
        List<Oppskrift> mineOppskrifter = repository.findByBrukerId(meg.getId());

        for (UkesmenyItem ui : items) {
            String tittel = ui.getOppskriftTittel();
            if (tittel == null || tittel.isBlank()) continue;

            // Prøv å finne matchende oppskrift i brukerens kokebok
            java.util.Optional<Oppskrift> match = mineOppskrifter.stream()
                .filter(o -> o.getTittel() != null && o.getTittel().equalsIgnoreCase(tittel))
                .findFirst();

            if (match.isPresent() && match.get().getIngredienser() != null) {
                // Legg til ingredienslinjer fra oppskriften
                for (String linje : match.get().getIngredienser().split("\n")) {
                    String trimmet = linje.trim();
                    if (!trimmet.isEmpty()) {
                        HandelListeItem hi = new HandelListeItem();
                        hi.setBrukerId(meg.getId());
                        hi.setTekst(trimmet);
                        hi.setKategori(ui.getDag() + " · " + ui.getMaltid());
                        handelListeRepository.save(hi);
                    }
                }
            } else {
                // Fritekst — legg til som påminnelse
                HandelListeItem hi = new HandelListeItem();
                hi.setBrukerId(meg.getId());
                hi.setTekst(ui.getDag() + " " + ui.getMaltid() + ": " + tittel);
                hi.setKategori("Ukesmeny");
                handelListeRepository.save(hi);
            }
        }
        return "redirect:/handleliste";
    }

    // ── UTFORDRINGER ──────────────────────────────────────────────
    @GetMapping("/utfordringer")
    public String visUtfordringer(Model model, @AuthenticationPrincipal OAuth2User principal) {
        List<Utfordring> aktive = utfordringRepository.findByAktivTrueOrderByOpprettetDesc();
        model.addAttribute("utfordringer", aktive);
        // For hver aktiv utfordring: hent oppskrifter med matching kategori/tittel
        if (!aktive.isEmpty()) {
            Utfordring ukens = aktive.get(0);
            model.addAttribute("ukensUtfordring", ukens);
            // Finn offentlige oppskrifter som matcher tittelen
            List<Oppskrift> bidrag = repository.findAll().stream()
                .filter(Oppskrift::isErOffentlig)
                .filter(o -> ukens.getTittel() != null &&
                    (o.getKategori() != null && o.getKategori().equalsIgnoreCase(ukens.getTittel()) ||
                     o.getTittel() != null && o.getTittel().toLowerCase().contains(ukens.getTittel().toLowerCase())))
                .collect(Collectors.toList());
            model.addAttribute("bidrag", bidrag);
        }
        return "utfordringer";
    }

    @PostMapping("/utfordringer/ny")
    public String nyUtfordring(@RequestParam String tittel,
                                @RequestParam(required = false) String beskrivelse,
                                @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";
        // Deaktiver gamle
        utfordringRepository.findByAktivTrueOrderByOpprettetDesc().forEach(u -> {
            u.setAktiv(false);
            utfordringRepository.save(u);
        });
        Utfordring u = new Utfordring();
        u.setTittel(tittel);
        u.setBeskrivelse(beskrivelse);
        u.setAktiv(true);
        utfordringRepository.save(u);
        return "redirect:/utfordringer";
    }

    // ── AI INGREDIENSER ───────────────────────────────────────────
    @GetMapping("/ai-forslag")
    public String visAiForslag(Model model) {
        return "ai-forslag";
    }

    @PostMapping("/ai-forslag")
    public String genererAiForslag(@RequestParam String ingredienser, Model model) {
        model.addAttribute("ingredienser", ingredienser);
        try {
            String prompt = lagIngredienserPrompt(ingredienser);
            String svar = aiOppskriftService.genererFritekst(prompt);
            model.addAttribute("aiSvar", svar);
        } catch (Exception e) {
            model.addAttribute("aiSvar", "Kunne ikke hente forslag akkurat nå. Prøv igjen.");
        }
        return "ai-forslag";
    }

    @ResponseBody
    @PostMapping("/api/ai-forslag")
    public Map<String, String> aiForslag(@RequestBody Map<String, String> body) {
        String ingredienser = body.getOrDefault("ingredienser", "");
        Map<String, String> result = new HashMap<>();
        if (ingredienser.isBlank()) {
            result.put("feil", "Ingen ingredienser oppgitt.");
            return result;
        }
        try {
            String svar = aiOppskriftService.genererFritekst(lagIngredienserPrompt(ingredienser));
            if (svar != null && svar.startsWith("RATE_LIMIT:")) {
                String sek = svar.replace("RATE_LIMIT:", "");
                result.put("rateLimitSek", sek);
            } else if (svar == null || svar.isBlank()) {
                result.put("rateLimitSek", "60");
            } else {
                result.put("svar", svar);
            }
        } catch (Exception e) {
            result.put("feil", "Klarte ikke kontakte AI akkurat nå. Prøv igjen.");
        }
        return result;
    }

    private String lagIngredienserPrompt(String ingredienser) {
        return "Jeg har følgende ingredienser: " + ingredienser +
            ". Foreslå 3 enkle norske oppskrifter jeg kan lage med disse. " +
            "For hver oppskrift: ### Tittel, en setnings beskrivelse, deretter **Ingredienser:** og **Fremgangsmåte:** med punkter. " +
            "Svar kun på norsk. Hold det kortfattet og praktisk.";
    }

    // ── HELPER ────────────────────────────────────────────────────
    private void lagrVarsel(Long mottakerBrukerId, String tekst, String lenke) {
        Varsel v = new Varsel();
        v.setMottakerBrukerId(mottakerBrukerId);
        v.setTekst(tekst);
        v.setLenke(lenke);
        varselRepository.save(v);
    }
}
