package com.example.matminne.service;

import com.example.matminne.model.Bruker;
import com.example.matminne.model.Samling;
import com.stripe.model.Account;
import com.stripe.model.AccountLink;
import com.stripe.model.checkout.Session;
import com.stripe.param.AccountCreateParams;
import com.stripe.param.AccountLinkCreateParams;
import com.stripe.param.checkout.SessionCreateParams;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Stripe Connect for skapere som selger betalte samlinger.
 *
 * Vi bruker Express-kontoer og «destination charges»: betalingen skjer på
 * plattformkontoen, skaperens andel overføres direkte til hennes Connect-konto,
 * og plattformens kutt tas som application fee. Dermed blir skaperens inntekt
 * aldri vår omsetning, og Stripe håndterer utbetaling og hennes skatterapport.
 */
@Service
public class StripeConnectService {

    private static final Logger log = LoggerFactory.getLogger(StripeConnectService.class);

    @Value("${app.base.url:http://localhost:8080}")
    private String baseUrl;

    @Value("${stripe.secret.key:}")
    private String stripeSecretKey;

    @Autowired private SkaperService skaperService;

    public boolean erKonfigurert() {
        return stripeSecretKey != null && !stripeSecretKey.isBlank();
    }

    // ── ONBOARDING ────────────────────────────────────────────────

    /**
     * Oppretter en Express-konto for skaperen hvis hun ikke har en, og
     * returnerer URL-en hun må gjennom for å fullføre onboarding hos Stripe.
     */
    public String startOnboarding(Bruker skaper) throws Exception {
        String kontoId = skaper.getStripeConnectId();

        if (kontoId == null || kontoId.isBlank()) {
            AccountCreateParams params = AccountCreateParams.builder()
                    .setType(AccountCreateParams.Type.EXPRESS)
                    .setCountry("NO")
                    .setEmail(skaper.getEpost())
                    .setCapabilities(AccountCreateParams.Capabilities.builder()
                            .setTransfers(AccountCreateParams.Capabilities.Transfers.builder()
                                    .setRequested(true).build())
                            .setCardPayments(AccountCreateParams.Capabilities.CardPayments.builder()
                                    .setRequested(true).build())
                            .build())
                    .setBusinessType(AccountCreateParams.BusinessType.INDIVIDUAL)
                    .putMetadata("brukerEpost", skaper.getEpost())
                    .build();
            Account konto = Account.create(params);
            kontoId = konto.getId();
            log.info("Opprettet Stripe Connect-konto {} for {}", kontoId, skaper.getEpost());
        }

        AccountLink lenke = AccountLink.create(AccountLinkCreateParams.builder()
                .setAccount(kontoId)
                .setRefreshUrl(baseUrl + "/skaper/onboarding")
                .setReturnUrl(baseUrl + "/skaper/onboarding/retur")
                .setType(AccountLinkCreateParams.Type.ACCOUNT_ONBOARDING)
                .build());

        // Lagres av kalleren sammen med brukeren
        skaper.setStripeConnectId(kontoId);
        return lenke.getUrl();
    }

    /**
     * Spør Stripe om kontoen faktisk kan motta penger nå.
     * Kalles etter at skaperen kommer tilbake fra onboarding.
     */
    public boolean sjekkOmKlar(String kontoId) {
        if (kontoId == null || kontoId.isBlank()) return false;
        try {
            Account konto = Account.retrieve(kontoId);
            return Boolean.TRUE.equals(konto.getChargesEnabled())
                    && Boolean.TRUE.equals(konto.getPayoutsEnabled());
        } catch (Exception e) {
            log.warn("Kunne ikke hente Connect-konto {}: {}", kontoId, e.getMessage());
            return false;
        }
    }

    /** Lenke til Stripes dashboard der skaperen ser sine egne utbetalinger. */
    public String dashboardLenke(String kontoId) throws Exception {
        return com.stripe.model.LoginLink.createOnAccount(kontoId).getUrl();
    }

    // ── KJØP ──────────────────────────────────────────────────────

    /**
     * Starter Checkout for et engangskjøp av en samling.
     * Pris sendes inline, så skaperen kan endre pris uten at vi må
     * synkronisere Product/Price-objekter hos Stripe.
     */
    public String startKjop(Samling samling, Bruker kjoper, Bruker skaper) throws Exception {
        int belop = samling.getPris();
        int andel = skaperService.plattformAndelAv(belop);

        SessionCreateParams.Builder params = SessionCreateParams.builder()
                .setMode(SessionCreateParams.Mode.PAYMENT)
                .setCustomerEmail(kjoper.getEpost())
                .addLineItem(SessionCreateParams.LineItem.builder()
                        .setQuantity(1L)
                        .setPriceData(SessionCreateParams.LineItem.PriceData.builder()
                                .setCurrency("nok")
                                .setUnitAmount((long) belop)
                                .setProductData(SessionCreateParams.LineItem.PriceData.ProductData.builder()
                                        .setName(samling.getNavn())
                                        .setDescription(beskrivelseFor(samling, skaper))
                                        .build())
                                .build())
                        .build())
                .setPaymentIntentData(SessionCreateParams.PaymentIntentData.builder()
                        .setApplicationFeeAmount((long) andel)
                        .setTransferData(SessionCreateParams.PaymentIntentData.TransferData.builder()
                                .setDestination(skaper.getStripeConnectId())
                                .build())
                        .build())
                .putMetadata("samlingId", String.valueOf(samling.getId()))
                .putMetadata("kjoperEpost", kjoper.getEpost())
                .putMetadata("skaperId", String.valueOf(skaper.getId()))
                .putMetadata("plattformAndel", String.valueOf(andel))
                .setSuccessUrl(baseUrl + "/samling/kjop/suksess?session_id={CHECKOUT_SESSION_ID}")
                .setCancelUrl(baseUrl + "/samling/" + samling.getId());

        Session sesjon = Session.create(params.build());
        log.info("Checkout startet for samling {} ({} øre, {} i andel) til {}",
                samling.getId(), belop, andel, kjoper.getEpost());
        return sesjon.getUrl();
    }

    private String beskrivelseFor(Samling samling, Bruker skaper) {
        String av = skaper != null ? " av " + skaper.getVisningsnavn() : "";
        if (samling.getBeskrivelse() != null && !samling.getBeskrivelse().isBlank()) {
            String b = samling.getBeskrivelse();
            return b.length() > 200 ? b.substring(0, 197) + "..." : b;
        }
        return "Digital kokebok" + av;
    }
}
