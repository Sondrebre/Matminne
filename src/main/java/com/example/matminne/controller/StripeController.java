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
import com.example.matminne.model.Bruker;
import com.example.matminne.repository.BrukerRepository;
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
        Bruker meg = brukerService.finnVedEpost(principal.getAttribute("email"));
        model.addAttribute("harAbonnement", meg != null && meg.isHarAbonnement());
        return "abonnement";
    }

    // ── START STRIPE CHECKOUT ─────────────────────────────────────
    @PostMapping("/abonnement/checkout")
    public String startCheckout(@AuthenticationPrincipal OAuth2User principal) {
        if (principal == null) return "redirect:/";
        if (stripeSecretKey.isBlank() || stripePriceId.isBlank()) {
            log.error("Stripe ikke konfigurert — mangler secret key eller price ID");
            return "redirect:/abonnement?feil=stripe-ikke-konfigurert";
        }
        String epost = principal.getAttribute("email");
        try {
            SessionCreateParams params = SessionCreateParams.builder()
                    .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
                    .setCustomerEmail(epost)
                    .addLineItem(SessionCreateParams.LineItem.builder()
                            .setPrice(stripePriceId)
                            .setQuantity(1L)
                            .build())
                    .putMetadata("brukerEpost", epost)
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
                        bruker.setHarAbonnement(true);
                        if (session.getCustomer() != null) {
                            bruker.setStripeCustomerId(session.getCustomer());
                        }
                        brukerRepository.save(bruker);
                        log.info("Abonnement aktivert for: {}", epost);
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
            case "customer.subscription.deleted":
            case "customer.subscription.paused": {
                event.getDataObjectDeserializer().getObject().ifPresent(obj -> {
                    com.stripe.model.Subscription sub = (com.stripe.model.Subscription) obj;
                    String customerId = sub.getCustomer();
                    brukerRepository.findByStripeCustomerId(customerId)
                            .ifPresent(b -> {
                                b.setHarAbonnement(false);
                                brukerRepository.save(b);
                                log.info("Abonnement deaktivert via webhook for customer: {}", customerId);
                            });
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
}
