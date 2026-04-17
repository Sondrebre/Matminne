package com.example.matminne.controller;

import com.example.matminne.model.*;
import com.example.matminne.repository.*;
import com.example.matminne.service.*;
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
import jakarta.transaction.Transactional;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.springframework.web.util.HtmlUtils;

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

    @GetMapping("/ingen-tilgang")
    public String ingenTilgang() {
        return "ingen-tilgang";
    }

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

    @GetMapping("/kokebok")
    public String visKokebok(Model model, @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";
        String epost = principal.getAttribute("email");
        Bruker meg = brukerService.finnVedEpost(epost);
        if (meg == null) {
            // Fallback: opprett bruker hvis TilgangsSjekk ikke rakk det
            meg = new Bruker();
            meg.setEpost(epost);
            meg.setFulltNavn(principal.getAttribute("name"));
            brukerService.lagreBruker(meg);
        }
        List<Oppskrift> mineOppskrifter = repository.findByBrukerId(meg.getId());
        Collections.reverse(mineOppskrifter);
        model.addAttribute("oppskrifter", mineOppskrifter);
        model.addAttribute("antall", mineOppskrifter.size());
        return "kokebok";
    }

    @GetMapping("/installer-app")
    public String visInstallerApp() {
        return "installer-app";
    }

    @PostMapping("/generer")
    public String genererFraUrl(@RequestParam String url, Model model,
                                @AuthenticationPrincipal OAuth2User principal) {
        Bruker meg = principal != null ? brukerService.finnVedEpost(principal.getAttribute("email")) : null;
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

    @PostMapping("/api/substitutt")
    @ResponseBody
    public ResponseEntity<Map<String, String>> genererSubstitutt(
            @RequestParam String ingrediens,
            @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
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

    @GetMapping("/api/pris/{id}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> hentPrisEstimat(@PathVariable Long id,
                                                               @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
        if (meg == null || !meg.isHarAbonnement()) return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        Oppskrift o = repository.findById(id).orElse(null);
        if (o == null) return ResponseEntity.notFound().build();
        Map<String, Object> resultat = aiOppskriftService.genererPrisEstimat(o.getIngredienser(), o.getPorsjoner());
        return ResponseEntity.ok(resultat);
    }

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
        boolean visTrending = (q == null || q.isBlank()) && (kategori == null || kategori.isBlank());
        if (visTrending) {
            List<Long> trendingIds = likeRepository.findTopLikedOppskriftIds(PageRequest.of(0, 6));
            List<Oppskrift> trending = trendingIds.stream()
                .map(tid -> repository.findById(tid).orElse(null))
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

    @GetMapping("/vennefeed")
    public String visVenneFeed(Model model, @AuthenticationPrincipal OAuth2User principal,
                               @RequestParam(required = false) String sok) {
        Bruker meg = principal != null ? brukerService.finnVedEpost(principal.getAttribute("email")) : null;
        model.addAttribute("oppskrifter", Collections.emptyList());
        model.addAttribute("sokeTekst", sok);
        if (meg != null) {
            List<Long> vennerIds = vennskapRepository.findByBrukerId(meg.getId())
                    .stream().map(Vennskap::getVennId).collect(Collectors.toList());
            if (!vennerIds.isEmpty()) {
                model.addAttribute("oppskrifter",
                    repository.findByBrukerIdInAndErOffentligTrueOrderByIdDesc(vennerIds));
            }
            if (sok != null && !sok.isBlank()) {
                List<Bruker> sokResultater = brukerService.sokEtterNavn(sok);
                model.addAttribute("sokResultater", sokResultater);
                Set<Long> fulgte = vennerIds.stream().collect(Collectors.toSet());
                model.addAttribute("fulgteBrukerIds", fulgte);
            }
        }
        return "vennefeed";
    }

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
        long antallFølgere = vennskapRepository.countByVennId(profilBruker.getId());
        long antallFølger  = vennskapRepository.findByBrukerId(profilBruker.getId()).size();
        model.addAttribute("erEgenProfil", principal != null && epost.equals(principal.getAttribute("email")));
        model.addAttribute("følgerAllerede", følgerAllerede);
        model.addAttribute("profilNavn", profilBruker.getFulltNavn());
        model.addAttribute("profilEpost", epost);
        model.addAttribute("profilBrukerBilde", profilBruker.getBildeUrl());
        model.addAttribute("oppskrifter", offentlige);
        model.addAttribute("antallFølgere", antallFølgere);
        model.addAttribute("antallFølger", antallFølger);
        model.addAttribute("profilBio", profilBruker.getBio());
        return "profil";
    }

    @GetMapping("/api/profil/{epost}/følgere")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> hentFølgere(@PathVariable String epost) {
        Bruker bruker = brukerService.finnVedEpost(epost);
        if (bruker == null) return ResponseEntity.notFound().build();
        List<Map<String, Object>> liste = vennskapRepository.findByVennId(bruker.getId())
                .stream()
                .map(v -> {
                    Bruker f = brukerService.findById(v.getBrukerId());
                    Map<String, Object> m = new HashMap<>();
                    m.put("navn",  f != null ? f.getFulltNavn() : "Ukjent");
                    m.put("epost", f != null ? f.getEpost() : "");
                    m.put("bilde", f != null ? f.getBildeUrl() : null);
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(liste);
    }

    @GetMapping("/api/profil/{epost}/følger")
    @ResponseBody
    public ResponseEntity<List<Map<String, Object>>> hentFølger(@PathVariable String epost) {
        Bruker bruker = brukerService.finnVedEpost(epost);
        if (bruker == null) return ResponseEntity.notFound().build();
        List<Map<String, Object>> liste = vennskapRepository.findByBrukerId(bruker.getId())
                .stream()
                .map(v -> {
                    Bruker f = brukerService.findById(v.getVennId());
                    Map<String, Object> m = new HashMap<>();
                    m.put("navn",  f != null ? f.getFulltNavn() : "Ukjent");
                    m.put("epost", f != null ? f.getEpost() : "");
                    m.put("bilde", f != null ? f.getBildeUrl() : null);
                    return m;
                })
                .collect(Collectors.toList());
        return ResponseEntity.ok(liste);
    }

    @Transactional
    @PostMapping("/følg/{vennEpost}")
    public String følgBruker(@PathVariable String vennEpost,
                             @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/profil/" + vennEpost;
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
        if (principal == null) return "redirect:/profil/" + vennEpost;
        Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
        Bruker denAndre = brukerService.finnVedEpost(vennEpost);
        if (meg != null && denAndre != null) {
            Vennskap v = vennskapRepository.findByBrukerIdAndVennId(meg.getId(), denAndre.getId());
            if (v != null) vennskapRepository.delete(v);
        }
        return "redirect:/profil/" + vennEpost;
    }

    @PostMapping("/lagre")
    public String lagreOppskrift(@ModelAttribute Oppskrift oppskrift,
                                 @AuthenticationPrincipal OAuth2User principal) {
        if (principal != null) {
            Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
            if (meg != null) {
                oppskrift.setBrukerId(meg.getId());
                oppskrift.setBrukerEpost(meg.getEpost());
                oppskrift.setBrukerNavn(principal.getAttribute("name"));
                if (oppskrift.getBildeUrl() == null || oppskrift.getBildeUrl().isEmpty())
                    oppskrift.setBildeUrl(DEFAULT_IMAGE);
                repository.save(oppskrift);
            }
        }
        return "redirect:/kokebok";
    }

    @PostMapping("/oppdater/{id}")
    public String oppdaterOppskrift(@PathVariable Long id,
                                    @ModelAttribute Oppskrift data,
                                    @AuthenticationPrincipal OAuth2User principal) {
        Oppskrift eks = repository.findById(id).orElse(null);
        if (principal == null) return "redirect:/";
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

    @PostMapping("/kopier/{id}")
    public String kopierOppskrift(@PathVariable Long id,
                                  @AuthenticationPrincipal OAuth2User principal) {
        Oppskrift original = repository.findById(id).orElse(null);
        if (original == null) return "redirect:/kokebok";
        if (principal != null) {
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
        }
        return "redirect:/kokebok";
    }

    @PostMapping("/slett/{id}")
    public String slettOppskrift(@PathVariable Long id,
                                 @AuthenticationPrincipal OAuth2User principal) {
        Oppskrift o = repository.findById(id).orElse(null);
        if (o != null && principal != null && o.getBrukerEpost().equals(principal.getAttribute("email")))
            repository.delete(o);
        return "redirect:/kokebok";
    }

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
        double snittRating = ratingRepository.snitRatingForOppskrift(id);
        int minRating = 0;
        if (innloggetBrukerId != null) {
            Rating eksRating = ratingRepository.findByBrukerIdAndOppskriftId(innloggetBrukerId, id);
            if (eksRating != null) minRating = eksRating.getVerdi();
        }
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

    @GetMapping("/ny-oppskrift")
    public String visNyOppskriftForm(Model model) {
        model.addAttribute("oppskrift", new Oppskrift());
        return "ny-oppskrift";
    }

    @GetMapping("/rediger/{id}")
    public String visRedigerForm(@PathVariable Long id, Model model,
                                 @AuthenticationPrincipal OAuth2User principal) {
        Oppskrift o = repository.findById(id).orElse(null);
        if (o == null || principal == null || !o.getBrukerEpost().equals(principal.getAttribute("email")))
            return "redirect:/kokebok";
        model.addAttribute("o", o);
        return "rediger";
    }

    @Transactional
    @PostMapping("/lik/{oppskriftId}")
    public String toggleLik(@PathVariable Long oppskriftId,
                            @AuthenticationPrincipal OAuth2User principal,
                            HttpServletRequest request) {
        if (principal != null) {
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
        }
        String referer = request.getHeader("Referer");
        return "redirect:" + (referer != null ? referer : "/detaljer/" + oppskriftId);
    }

    @Transactional
    @PostMapping("/kommentar/{oppskriftId}")
    public String leggTilKommentar(@PathVariable Long oppskriftId,
                                   @RequestParam String tekst,
                                   @RequestParam(required = false) Long foreldreId,
                                   @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/detaljer/" + oppskriftId;
        Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
        if (meg != null && tekst != null && !tekst.isBlank() && tekst.length() <= 1000) {
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
        Kommentar k = kommentarRepository.findById(id).orElse(null);
        if (k == null) return "redirect:/kokebok";
        if (principal != null) {
            Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
            if (meg != null && k.getBrukerId().equals(meg.getId()))
                kommentarRepository.delete(k);
        }
        return "redirect:/detaljer/" + k.getOppskriftId();
    }

    @GetMapping("/varsler")
    public String visVarsler(Model model, @AuthenticationPrincipal OAuth2User principal) {
        model.addAttribute("varsler", Collections.emptyList());
        if (principal != null) {
            Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
            if (meg != null) {
                List<Varsel> varsler = varselRepository.findByMottakerBrukerIdOrderByOpprettetDesc(meg.getId());
                varsler.forEach(v -> { v.setLest(true); varselRepository.save(v); });
                model.addAttribute("varsler", varsler);
            }
        }
        return "varsler";
    }

    @GetMapping("/samlinger")
    public String visSamlinger(Model model, @AuthenticationPrincipal OAuth2User principal) {
        model.addAttribute("samlinger", Collections.emptyList());
        model.addAttribute("antallPerSamling", new HashMap<Long, Long>());
        model.addAttribute("forsideBilder", new HashMap<Long, String>());
        if (principal != null) {
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
        }
        return "samlinger";
    }

    @PostMapping("/samlinger/ny")
    public String nyaSamling(@RequestParam String navn,
                             @RequestParam(required = false) String beskrivelse,
                             @AuthenticationPrincipal OAuth2User principal) {
        if (principal != null) {
            Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
            if (meg != null && navn != null && !navn.isBlank()) {
                Samling s = new Samling();
                s.setBrukerId(meg.getId());
                s.setNavn(navn.trim());
                s.setBeskrivelse(beskrivelse != null ? beskrivelse.trim() : "");
                samlingRepository.save(s);
            }
        }
        return "redirect:/samlinger";
    }

    @GetMapping("/samlinger/{id}")
    public String visSamlingDetaljer(@PathVariable Long id, Model model,
                                     @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/samlinger";
        Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
        if (meg == null) return "redirect:/samlinger";
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
        if (principal != null) {
            Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
            Samling s = samlingRepository.findById(id).orElse(null);
            if (meg != null && s != null && s.getBrukerId().equals(meg.getId())) {
                if (!samlingOppskriftRepository.existsBySamlingIdAndOppskriftId(id, oppskriftId))
                    samlingOppskriftRepository.save(new SamlingOppskrift(id, oppskriftId));
            }
        }
        return "redirect:/samlinger/" + id;
    }

    @PostMapping("/samlinger/{id}/fjern/{oppskriftId}")
    public String fjernFraSamling(@PathVariable Long id, @PathVariable Long oppskriftId,
                                  @AuthenticationPrincipal OAuth2User principal) {
        if (principal != null) {
            Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
            Samling s = samlingRepository.findById(id).orElse(null);
            if (meg != null && s != null && s.getBrukerId().equals(meg.getId())) {
                samlingOppskriftRepository.findBySamlingIdAndOppskriftId(id, oppskriftId)
                        .ifPresent(samlingOppskriftRepository::delete);
            }
        }
        return "redirect:/samlinger/" + id;
    }

    @PostMapping("/samlinger/slett/{id}")
    public String slettSamling(@PathVariable Long id,
                               @AuthenticationPrincipal OAuth2User principal) {
        if (principal != null) {
            Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
            Samling s = samlingRepository.findById(id).orElse(null);
            if (meg != null && s != null && s.getBrukerId().equals(meg.getId())) {
                samlingOppskriftRepository.findBySamlingId(id).forEach(samlingOppskriftRepository::delete);
                samlingRepository.delete(s);
            }
        }
        return "redirect:/samlinger";
    }

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
        String epost = principal.getAttribute("email");
        Bruker meg = brukerService.finnVedEpost(epost);
        if (meg != null && nyBildeUrl != null && !nyBildeUrl.isBlank()) {
            meg.setBildeUrl(nyBildeUrl);
            brukerService.lagreBruker(meg);
        }
        return "redirect:/profil/" + epost;
    }

    @PostMapping("/oppdater-bio")
    public String oppdaterBio(@RequestParam String bio,
                              @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";
        String epost = principal.getAttribute("email");
        Bruker meg = brukerService.finnVedEpost(epost);
        if (meg != null) {
            meg.setBio(bio != null ? bio.trim() : "");
            brukerService.lagreBruker(meg);
        }
        return "redirect:/profil/" + epost;
    }

    @GetMapping("/sok")
    public String globalSok(@RequestParam(required = false) String q, Model model,
                            @AuthenticationPrincipal OAuth2User principal) {
        Map<String, String> treff = new HashMap<>();
        if (q != null && !q.isBlank()) {
            brukerService.sokEtterNavn(q).forEach(b -> treff.put(b.getFulltNavn(), b.getEpost()));
        }
        model.addAttribute("query", q != null ? q : "");
        model.addAttribute("treff", treff);
        return "sok-resultat";
    }

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

    @PostMapping("/pdf")
    public ResponseEntity<byte[]> genererPdf(@RequestParam Long id,
                                             @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        try {
            Oppskrift o = repository.findById(id).orElse(null);
            if (o == null) return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
            String ingSafe  = o.getIngredienser()  != null ? HtmlUtils.htmlEscape(o.getIngredienser()).replace("\n", "<br>")  : "";
            String stegSafe = o.getFremgangsmate()  != null ? HtmlUtils.htmlEscape(o.getFremgangsmate()).replace("\n", "<br>") : "";
            String bildeSrc = (o.getBildeUrl() != null && !o.getBildeUrl().isEmpty()) ? HtmlUtils.htmlEscape(o.getBildeUrl()) : "";
            String bildeHtml = bildeSrc.isEmpty() ? "" :
                "<div style='width:100%;height:220px;overflow:hidden;border-radius:16px;margin-bottom:32px;'>" +
                "<img src='" + bildeSrc + "' style='width:100%;height:100%;object-fit:cover;'/></div>";
            String metaHtml = "";
            if (o.getTilberedningstid() != null) metaHtml += "<span style='margin-right:16px;'>⏱ " + HtmlUtils.htmlEscape(String.valueOf(o.getTilberedningstid())) + " min</span>";
            if (o.getPorsjoner()        != null) metaHtml += "<span style='margin-right:16px;'>🍽 " + HtmlUtils.htmlEscape(String.valueOf(o.getPorsjoner())) + " porsjoner</span>";
            if (o.getVanskelighet()     != null) metaHtml += "<span>⭐ " + HtmlUtils.htmlEscape(o.getVanskelighet()) + "</span>";
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
                "  <h1>" + HtmlUtils.htmlEscape(o.getTittel()) + "</h1>" +
                "  <div class='header-meta'>Av " + HtmlUtils.htmlEscape(o.getBrukerNavn() != null ? o.getBrukerNavn() : "ukjent") + "</div>" +
                (metaHtml.isEmpty() ? "" : "<div class='meta-badges'>" + metaHtml + "</div>") +
                "</div>" +
                "<div class='body'>" +
                bildeHtml +
                (ingSafe.isEmpty()  ? "" : "<div class='section'><div class='section-title'>Ingredienser</div><div class='section-content'>" + ingSafe + "</div></div>") +
                (stegSafe.isEmpty() ? "" : "<div class='section'><div class='section-title'>Fremgangsmåte</div><div class='section-content'>" + stegSafe + "</div></div>") +
                "<div class='footer'><span>matminne.no</span><span>" + HtmlUtils.htmlEscape(o.getTittel()) + "</span></div>" +
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

    @PostMapping("/rating/{oppskriftId}")
    public String giRating(@PathVariable Long oppskriftId,
                           @RequestParam int verdi,
                           @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/detaljer/" + oppskriftId;
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

    @GetMapping("/handleliste")
    public String visHandelListe(Model model, @AuthenticationPrincipal OAuth2User principal) {
        Bruker meg = principal != null ? brukerService.finnVedEpost(principal.getAttribute("email")) : null;
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
        if (principal != null) {
            Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
            if (meg != null && tekst != null && !tekst.isBlank()) {
                HandelListeItem item = new HandelListeItem();
                item.setBrukerId(meg.getId());
                item.setTekst(tekst.trim());
                item.setKategori(kategori);
                handelListeRepository.save(item);
            }
        }
        return "redirect:/handleliste";
    }

    @PostMapping("/handleliste/fra-oppskrift/{id}")
    public String leggTilFraOppskrift(@PathVariable Long id,
                                      @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/handleliste";
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
    @ResponseBody
    public ResponseEntity<String> toggleHandelVare(@PathVariable Long id,
                                                   @AuthenticationPrincipal OAuth2User principal) {
        HandelListeItem item = handelListeRepository.findById(id).orElse(null);
        if (item == null) return ResponseEntity.notFound().build();
        item.setFerdig(!item.isFerdig());
        handelListeRepository.save(item);
        return ResponseEntity.ok("ok");
    }

    @PostMapping("/handleliste/slett/{id}")
    @ResponseBody
    public ResponseEntity<String> slettHandelVare(@PathVariable Long id,
                                                  @AuthenticationPrincipal OAuth2User principal) {
        handelListeRepository.deleteById(id);
        return ResponseEntity.ok("ok");
    }

    @PostMapping("/handleliste/rydd")
    @ResponseBody
    public ResponseEntity<String> ryddHandelListe(@AuthenticationPrincipal OAuth2User principal) {
        if (principal != null) {
            Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
            if (meg != null) handelListeRepository.deleteByBrukerIdAndFerdigTrue(meg.getId());
        }
        return ResponseEntity.ok("ok");
    }

    @PostMapping("/handleliste/slett-alt")
    @ResponseBody
    public ResponseEntity<String> slettAltHandelListe(@AuthenticationPrincipal OAuth2User principal) {
        if (principal != null) {
            Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
            if (meg != null) handelListeRepository.deleteByBrukerId(meg.getId());
        }
        return ResponseEntity.ok("ok");
    }

    @PostMapping("/handleliste/endre/{id}")
    @ResponseBody
    public ResponseEntity<String> endreHandelVare(@PathVariable Long id,
                                                  @RequestBody Map<String, String> body,
                                                  @AuthenticationPrincipal OAuth2User principal) {
        HandelListeItem item = handelListeRepository.findById(id).orElse(null);
        if (item == null) return ResponseEntity.notFound().build();
        String nyTekst = body.getOrDefault("tekst", "").trim();
        if (nyTekst.isBlank()) return ResponseEntity.badRequest().body("Tom tekst");
        item.setTekst(nyTekst);
        handelListeRepository.save(item);
        return ResponseEntity.ok("ok");
    }

    @PostMapping("/api/handleliste/smartsorter")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> smartSorterHandleliste(
            @RequestBody Map<String, List<String>> body,
            @AuthenticationPrincipal OAuth2User principal) {
        List<String> varer = body.getOrDefault("varer", List.of());
        Map<String, Object> res = aiOppskriftService.smartSorterHandleliste(varer);
        if (res.containsKey("feil")) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(res);
        }
        return ResponseEntity.ok(res);
    }

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
                Math.max(0, Math.min(7, kjottMiddager)), allergier, porsjoner, ekstraOnsker);
        if (aiSvar.startsWith("FEIL") || aiSvar.startsWith("RATE_LIMIT")) {
            return "redirect:/ukesmeny?feil=ai";
        }
        List<UkesmenyItem> eksisterende = ukesmenyRepository.findByBrukerId(meg.getId());
        ukesmenyRepository.deleteAll(eksisterende);
        String[][] dagPar = {
            {"mandag","Mandag"},{"tirsdag","Tirsdag"},{"onsdag","Onsdag"},
            {"torsdag","Torsdag"},{"fredag","Fredag"},{"lordag","Lørdag"},{"sondag","Søndag"}
        };
        String[][] maltidPar = {{"frokost","Frokost"},{"lunsj","Lunsj"},{"middag","Middag"}};
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

    @GetMapping("/ukesmeny")
    public String visUkesmeny(Model model, @AuthenticationPrincipal OAuth2User principal) {
        Bruker meg = principal != null ? brukerService.finnVedEpost(principal.getAttribute("email")) : null;
        List<UkesmenyItem> items = Collections.emptyList();
        if (meg != null) items = ukesmenyRepository.findByBrukerId(meg.getId());
        List<String> dager = List.of("Mandag", "Tirsdag", "Onsdag", "Torsdag", "Fredag", "Lørdag", "Søndag");
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
        if (tittel == null || tittel.isBlank()) return "redirect:/ukesmeny";
        if (principal != null) {
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
        }
        return "redirect:/ukesmeny";
    }

    @PostMapping("/ukesmeny/fjern/{id}")
    public String fjernFraUkesmeny(@PathVariable Long id,
                                   @AuthenticationPrincipal OAuth2User principal) {
        ukesmenyRepository.deleteById(id);
        return "redirect:/ukesmeny";
    }

    @PostMapping("/ukesmeny/til-handleliste")
    public String ukesmenyTilHandelListe(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/handleliste";
        Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
        if (meg == null) return "redirect:/handleliste";
        List<UkesmenyItem> items = ukesmenyRepository.findByBrukerId(meg.getId());
        List<Oppskrift> mineOppskrifter = repository.findByBrukerId(meg.getId());
        for (UkesmenyItem ui : items) {
            String tittel = ui.getOppskriftTittel();
            if (tittel == null || tittel.isBlank()) continue;
            java.util.Optional<Oppskrift> match = mineOppskrifter.stream()
                .filter(o -> o.getTittel() != null && o.getTittel().equalsIgnoreCase(tittel))
                .findFirst();
            if (match.isPresent() && match.get().getIngredienser() != null) {
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
                HandelListeItem hi = new HandelListeItem();
                hi.setBrukerId(meg.getId());
                hi.setTekst(ui.getDag() + " " + ui.getMaltid() + ": " + tittel);
                hi.setKategori("Ukesmeny");
                handelListeRepository.save(hi);
            }
        }
        return "redirect:/handleliste";
    }

    @GetMapping("/utfordringer")
    public String visUtfordringer(Model model, @AuthenticationPrincipal OAuth2User principal) {
        List<Utfordring> aktive = utfordringRepository.findByAktivTrueOrderByOpprettetDesc();
        model.addAttribute("utfordringer", aktive);
        if (!aktive.isEmpty()) {
            Utfordring ukens = aktive.get(0);
            model.addAttribute("ukensUtfordring", ukens);
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

    @GetMapping("/ai-forslag")
    public String visAiForslag(Model model, @AuthenticationPrincipal OAuth2User principal) {
        Bruker meg = principal != null ? brukerService.finnVedEpost(principal.getAttribute("email")) : null;
        model.addAttribute("harAbonnement", meg != null && meg.isHarAbonnement());
        return "ai-forslag";
    }

    @PostMapping("/ai-forslag")
    public String genererAiForslag(@RequestParam String ingredienser, Model model,
                                   @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";
        Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
        if (meg == null || !meg.isHarAbonnement()) return "redirect:/abonnement";
        model.addAttribute("ingredienser", ingredienser);
        try {
            String svar = aiOppskriftService.genererFritekst(lagIngredienserPrompt(ingredienser));
            model.addAttribute("aiSvar", svar);
        } catch (Exception e) {
            model.addAttribute("aiSvar", "Kunne ikke hente forslag akkurat nå. Prøv igjen.");
        }
        return "ai-forslag";
    }

    @PostMapping("/api/ai-forslag")
    @ResponseBody
    public Map<String, String> aiForslag(@RequestBody Map<String, String> body,
                                         @AuthenticationPrincipal OAuth2User principal) {
        Map<String, String> result = new HashMap<>();
        if (principal == null) { result.put("feil", "Ikke innlogget."); return result; }
        Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
        if (meg == null || !meg.isHarAbonnement()) { result.put("feil", "Krever abonnement."); return result; }
        String ingredienser = body.getOrDefault("ingredienser", "");
        if (ingredienser.isBlank()) {
            result.put("feil", "Ingen ingredienser oppgitt.");
            return result;
        }
        try {
            String svar = aiOppskriftService.genererFritekst(lagIngredienserPrompt(ingredienser));
            if (svar != null && svar.startsWith("RATE_LIMIT:")) {
                result.put("rateLimitSek", svar.replace("RATE_LIMIT:", ""));
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


    @GetMapping("/naeringstrener")
    public String visNaeringstrener(Model model, @AuthenticationPrincipal OAuth2User principal) {
        Bruker meg = principal != null ? brukerService.finnVedEpost(principal.getAttribute("email")) : null;
        model.addAttribute("harAbonnement", meg != null && meg.isHarAbonnement());
        model.addAttribute("brukerEpost", principal != null ? principal.getAttribute("email") : null);
        return "naeringstrener";
    }

    @PostMapping("/api/naeringstrener/generer")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> genererDagsmeny(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
        if (meg == null || !meg.isHarAbonnement()) return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).build();
        String maal = body.getOrDefault("maal", "").trim();
        if (maal.isBlank()) return ResponseEntity.badRequest().build();
        String kjønn    = body.getOrDefault("kjonn", "").trim();
        String aktivitet = body.getOrDefault("aktivitet", "").trim();
        Integer vekt  = parseIntOrNull(body.get("vekt"));
        Integer høyde = parseIntOrNull(body.get("hoyde"));
        Integer alder = parseIntOrNull(body.get("alder"));
        Map<String, Object> res = aiOppskriftService.genererDagsmenyFraMal(
                maal, kjønn, vekt, høyde, alder, aktivitet);
        return ResponseEntity.ok(res);
    }

    private Integer parseIntOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    @PostMapping("/api/ukesmeny/oppskrift")
    @ResponseBody
    public ResponseEntity<Map<String, String>> genererUkesmenyOppskrift(
            @RequestBody Map<String, String> body,
            @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        String tittel = body.getOrDefault("tittel", "").trim();
        int porsjoner = 2;
        try { porsjoner = Integer.parseInt(body.getOrDefault("porsjoner", "2")); } catch (Exception ignored) {}
        if (tittel.isBlank()) return ResponseEntity.badRequest().build();
        Map<String, String> res = aiOppskriftService.genererOppskriftFraTittel(tittel, porsjoner);
        return ResponseEntity.ok(res);
    }

    private String lagIngredienserPrompt(String ingredienser) {
        return "Jeg har følgende ingredienser: " + ingredienser +
            ". Foreslå 3 enkle norske oppskrifter jeg kan lage med disse. " +
            "For hver oppskrift: ### Tittel, en setnings beskrivelse, deretter **Ingredienser:** og **Fremgangsmåte:** med punkter. " +
            "Svar kun på norsk. Hold det kortfattet og praktisk.";
    }

    private void lagrVarsel(Long mottakerBrukerId, String tekst, String lenke) {
        Varsel v = new Varsel();
        v.setMottakerBrukerId(mottakerBrukerId);
        v.setTekst(tekst);
        v.setLenke(lenke);
        varselRepository.save(v);
    }
}
