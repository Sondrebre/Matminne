package com.example.matminne.controller;

import com.stripe.Stripe;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.model.Event;
import com.stripe.model.checkout.Session;
import com.stripe.net.Webhook;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import com.example.matminne.model.Abonnement;
import com.example.matminne.model.Bruker;
import com.example.matminne.repository.BrukerRepository;
import com.example.matminne.repository.OppskriftRepository;
import com.example.matminne.service.AbonnementService;
import com.example.matminne.service.BrukerService;

@Controller
public class StripeController {

    private static final Logger log = LoggerFactory.getLogger(StripeController.class);

    @Value("${stripe.secret.key:}")
    private String stripeSecretKey;

    @Value("${stripe.price.id:}")
    private String stripePriceId;

    @Value("${stripe.webhook.secret:}")
    private String webhookSecret;

    @Value("${app.kampanje.kode:}")
    private String kampanjeKode;

    @Value("${app.base.url:http://localhost:8080}")
    private String baseUrl;

    @Autowired
    private BrukerService brukerService;

    @Autowired
    private BrukerRepository brukerRepository;

    @Autowired
    private com.example.matminne.service.KjopService kjopService;

    @Autowired
    private AbonnementService abonnementService;

    @Autowired
    private OppskriftRepository oppskriftRepository;

    @PostConstruct
    public void init() {
        if (stripeSecretKey != null && !stripeSecretKey.isBlank()) {
            Stripe.apiKey = stripeSecretKey;
            log.info("Stripe initialisert");
        } else {
            log.warn("Stripe API-nøkkel mangler — betalingsfunksjoner er deaktivert");
        }
    }

    // ── ABONNEMENTS-SIDE ──────────────────────────────────────────
    @GetMapping("/abonnement")
    public String visAbonnement(Model model, @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";
        String epost = principal.getAttribute("email");
        Bruker meg = brukerService.finnVedEpost(epost);

        long antallOppskrifter = meg != null ? oppskriftRepository.countByBrukerId(meg.getId()) : 0;
        Abonnement plan = meg != null ? meg.gjeldendePlan() : Abonnement.GRATIS;

        model.addAttribute("harAbonnement", meg != null && meg.isHarAbonnement());
        model.addAttribute("brukerEpost", epost);
        model.addAttribute("plan", plan);
        model.addAttribute("niva", Abonnement.betalte());
        model.addAttribute("tilgjengelig", abonnementService.tilgjengelighet());
        model.addAttribute("antallOppskrifter", antallOppskrifter);
        model.addAttribute("gratisGrense", Abonnement.GRATIS.getOppskriftGrense());
        // Foreslår minste plan som rommer det brukeren alt har lagret
        model.addAttribute("anbefalt", Abonnement.minsteSomRommer(antallOppskrifter));
        if (meg != null && meg.getBildeUrl() != null) model.addAttribute("profilBilde", meg.getBildeUrl());
        return "abonnement";
    }

    // ── START STRIPE CHECKOUT ─────────────────────────────────────
    @PostMapping("/abonnement/checkout")
    public String startCheckout(@RequestParam(required = false) String niva,
                                @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";

        Abonnement valgt = Abonnement.fraNavn(niva);
        if (valgt.erGratis()) return "redirect:/abonnement?feil=ugyldig-niva";

        if (stripeSecretKey.isBlank()) {
            log.error("Stripe ikke konfigurert — mangler secret key");
            return "redirect:/abonnement?feil=stripe-ikke-konfigurert";
        }

        String prisId = abonnementService.prisIdFor(valgt).orElse(null);
        if (prisId == null) {
            log.error("Mangler Stripe-pris for nivå {} (stripe.price.{})", valgt, valgt.prisNokkel());
            return "redirect:/abonnement?feil=stripe-ikke-konfigurert";
        }

        String epost = principal.getAttribute("email");
        try {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setCustomerEmail(epost)
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setPrice(prisId)
                            .setQuantity(1L)
                            .build())
                    .putMetadata("brukerEpost", epost)
                    .putMetadata("niva", valgt.name())
                    .setSuccessUrl(baseUrl + "/abonnement/suksess?session_id={CHECKOUT_SESSION_ID}")
                    .setCancelUrl(baseUrl + "/abonnement")
                    .build();
            Session session = Session.create(params);
            return "redirect:" + session.getUrl();
        } catch (Exception e) {
            log.error("Feil ved oppretting av Stripe-sesjon: {}", e.getMessage());
            return "redirect:/abonnement?feil=teknisk-feil";
        }
    }

    // ── SUKSESS ETTER BETALING ────────────────────────────────────
    @GetMapping("/abonnement/suksess")
    public String betalingSuksess(@RequestParam String session_id,
                                   @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";
        try {
            Session session = Session.retrieve(session_id);
            if ("paid".equals(session.getPaymentStatus()) ||
                "complete".equals(session.getStatus())) {
                String epost = session.getMetadata().get("brukerEpost");
                if (epost != null) {
                    Bruker bruker = brukerService.finnVedEpost(epost);
                    if (bruker != null) {
                        Abonnement niva = Abonnement.fraNavn(session.getMetadata().get("niva"));
                        bruker.setHarAbonnement(true);
                        if (!niva.erGratis()) bruker.setAbonnementNiva(niva);
                        if (session.getCustomer() != null) {
                            bruker.setStripeCustomerId(session.getCustomer());
                        }
                        brukerRepository.save(bruker);
                        log.info("Abonnement {} aktivert for: {}", niva, epost);
                    }
                }
            }
        } catch (Exception e) {
            log.error("Feil ved verifisering av Stripe-sesjon: {}", e.getMessage());
        }
        return "redirect:/abonnement?suksess=true";
    }

    // ── KAMPANJEKODE ──────────────────────────────────────────────
    @PostMapping("/abonnement/kampanje")
    public String aktiverKampanje(@RequestParam String kode,
                                   @AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";
        if (kampanjeKode.isBlank()) {
            return "redirect:/abonnement?feil=ugyldig";
        }
        if (kampanjeKode.equalsIgnoreCase(kode.trim())) {
            Bruker bruker = brukerService.finnVedEpost(principal.getAttribute("email"));
            if (bruker != null) {
                bruker.setHarAbonnement(true);
                brukerRepository.save(bruker);
                log.info("Kampanjekode aktivert for: {}", bruker.getEpost());
            }
            return "redirect:/abonnement?suksess=true";
        }
        return "redirect:/abonnement?feil=ugyldig";
    }

    // ── AVBRYT ABONNEMENT ─────────────────────────────────────────
    @PostMapping("/abonnement/avbryt")
    public String avbrytAbonnement(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";
        Bruker bruker = brukerService.finnVedEpost(principal.getAttribute("email"));
        if (bruker != null) {
            bruker.setHarAbonnement(false);
            brukerRepository.save(bruker);
            log.info("Abonnement avsluttet for: {}", bruker.getEpost());
        }
        return "redirect:/abonnement";
    }

    // ── STRIPE WEBHOOK ────────────────────────────────────────────
    @PostMapping("/stripe/webhook")
    @ResponseBody
    public ResponseEntity<String> handleWebhook(
            @RequestBody String payload,
            @RequestHeader(value = "Stripe-Signature", required = false) String sigHeader) {

        if (webhookSecret.isBlank()) {
            log.error("Webhook-secret mangler — avviser webhook-forespørsel");
            return ResponseEntity.status(401).body("Webhook ikke konfigurert");
        }

        Event event;
        try {
            event = Webhook.constructEvent(payload, sigHeader, webhookSecret);
        } catch (SignatureVerificationException e) {
            log.warn("Ugyldig Stripe-webhook-signatur");
            return ResponseEntity.badRequest().body("Ugyldig signatur");
        }

        switch (event.getType()) {
            // Engangskjøp av betalt samling. Viktigst av alle: fanger kjøpet
            // selv om brukeren lukker fanen før suksess-redirecten rekker å kjøre.
            case "checkout.session.completed": {
                event.getDataObjectDeserializer().getObject().ifPresent(obj -> {
                    Session sesjon = (Session) obj;
                    try {
                        kjopService.registrer(sesjon);
                    } catch (Exception e) {
                        log.error("Webhook kunne ikke registrere kjøp {}: {}",
                                sesjon.getId(), e.getMessage());
                    }
                });
                break;
            }

            // Connect-konto oppdatert — skaperen kan ha fullført onboarding
            case "account.updated": {
                event.getDataObjectDeserializer().getObject().ifPresent(obj -> {
                    com.stripe.model.Account konto = (com.stripe.model.Account) obj;
                    boolean klar = Boolean.TRUE.equals(konto.getChargesEnabled())
                            && Boolean.TRUE.equals(konto.getPayoutsEnabled());
                    brukerRepository.findByStripeConnectId(konto.getId()).ifPresent(b -> {
                        if (b.isConnectKlar() != klar) {
                            b.setConnectKlar(klar);
                            brukerRepository.save(b);
                            log.info("Connect-status for {} satt til {}", b.getEpost(), klar);
                        }
                    });
                });
                break;
            }

            case "customer.subscription.deleted":
            case "customer.subscription.paused": {
                event.getDataObjectDeserializer().getObject().ifPresent(obj -> {
                    com.stripe.model.Subscription sub = (com.stripe.model.Subscription) obj;
                    String customerId = sub.getCustomer();
                    brukerRepository.findByStripeCustomerId(customerId)
                            .ifPresent(b -> {
                                b.setHarAbonnement(false);
                                // Nivået beholdes, slik at en gjenopptakelse
                                // treffer samme plan som før
                                brukerRepository.save(b);
                                log.info("Abonnement deaktivert via webhook for customer: {}", customerId);
                            });
                });
                break;
            }

            // Oppgradering eller nedgradering: prisen på abonnementet er endret
            case "customer.subscription.updated": {
                event.getDataObjectDeserializer().getObject().ifPresent(obj -> {
                    com.stripe.model.Subscription sub = (com.stripe.model.Subscription) obj;
                    nivaFraAbonnement(sub).ifPresent(niva ->
                        brukerRepository.findByStripeCustomerId(sub.getCustomer())
                                .ifPresent(b -> {
                                    boolean aktiv = "active".equals(sub.getStatus())
                                            || "trialing".equals(sub.getStatus());
                                    if (b.getAbonnementNiva() != niva || b.isHarAbonnement() != aktiv) {
                                        b.setAbonnementNiva(niva);
                                        b.setHarAbonnement(aktiv);
                                        brukerRepository.save(b);
                                        log.info("Abonnement for {} endret til {} (aktiv: {})",
                                                b.getEpost(), niva, aktiv);
                                    }
                                }));
                });
                break;
            }
            case "invoice.payment_succeeded": {
                event.getDataObjectDeserializer().getObject().ifPresent(obj -> {
                    com.stripe.model.Invoice invoice = (com.stripe.model.Invoice) obj;
                    String customerId = invoice.getCustomer();
                    brukerRepository.findByStripeCustomerId(customerId)
                            .ifPresent(b -> {
                                b.setHarAbonnement(true);
                                brukerRepository.save(b);
                                log.info("Abonnement fornyet via webhook for customer: {}", customerId);
                            });
                });
                break;
            }
            default:
                break;
        }
        return ResponseEntity.ok("ok");
    }

    /**
     * Finner abonnementsnivået ut fra prisen på Stripe-abonnementet.
     * Tom hvis prisen ikke er én av våre konfigurerte nivåpriser.
     */
    private java.util.Optional<Abonnement> nivaFraAbonnement(com.stripe.model.Subscription sub) {
        try {
            if (sub.getItems() == null || sub.getItems().getData() == null) return java.util.Optional.empty();
            return sub.getItems().getData().stream()
                    .filter(i -> i.getPrice() != null && i.getPrice().getId() != null)
                    .map(i -> abonnementService.nivaFor(i.getPrice().getId()))
                    .filter(java.util.Optional::isPresent)
                    .map(java.util.Optional::get)
                    .findFirst();
        } catch (Exception e) {
            log.warn("Kunne ikke lese nivå fra abonnement {}: {}", sub.getId(), e.getMessage());
            return java.util.Optional.empty();
        }
    }
}
