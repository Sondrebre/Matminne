package com.example.matminne.controller;

import com.example.matminne.model.Abonnement;
import com.example.matminne.model.Bruker;
import com.example.matminne.repository.*;
import com.example.matminne.service.AiOppskriftService;
import com.example.matminne.service.BrukerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Collections;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Abonnementssiden med nivådelt pris, og grensen slik den håndheves.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        // Bare noen nivåer har pris satt — resten skal vises som "Kommer"
        "stripe.price.niva-30=price_test30",
        "stripe.price.niva-150=price_test150",
        "stripe.price.id="
})
class AbonnementSideTest {

    private static final long BRUKER_ID = 1L;

    @Autowired private WebApplicationContext wac;
    private MockMvc mockMvc;

    @MockitoBean private BrukerService brukerService;
    @MockitoBean private AiOppskriftService aiOppskriftService;
    @MockitoBean private OppskriftRepository oppskriftRepository;
    @MockitoBean private LikeRepository likeRepository;
    @MockitoBean private KommentarRepository kommentarRepository;
    @MockitoBean private VarselRepository varselRepository;
    @MockitoBean private SamlingRepository samlingRepository;
    @MockitoBean private SamlingOppskriftRepository samlingOppskriftRepository;
    @MockitoBean private RatingRepository ratingRepository;
    @MockitoBean private HandelListeRepository handelListeRepository;
    @MockitoBean private UkesmenyRepository ukesmenyRepository;
    @MockitoBean private UtfordringRepository utfordringRepository;
    @MockitoBean private VennskapRepository vennskapRepository;
    @MockitoBean private KjopRepository kjopRepository;
    @MockitoBean private BrukerRepository brukerRepository;

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(wac)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
        when(varselRepository.countByMottakerBrukerIdAndLestFalse(anyLong())).thenReturn(0L);
        when(samlingRepository.findByBrukerIdOrderByOpprettetDesc(anyLong()))
                .thenReturn(Collections.emptyList());
    }

    private Bruker bruker(Abonnement niva, boolean aktiv) {
        Bruker b = new Bruker("Test", "test@test.no");
        b.setId(BRUKER_ID);
        b.setAbonnementNiva(niva);
        b.setHarAbonnement(aktiv);
        when(brukerService.finnVedEpost("test@test.no")).thenReturn(b);
        return b;
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor somMeg() {
        return oauth2Login().attributes(a -> {
            a.put("email", "test@test.no");
            a.put("name", "Test");
        });
    }

    // ── SIDEN ─────────────────────────────────────────────────────

    @Test
    void abonnementssiden_viserAlleSyvNivaer() throws Exception {
        bruker(Abonnement.GRATIS, false);
        when(oppskriftRepository.countByBrukerId(BRUKER_ID)).thenReturn(5L);

        mockMvc.perform(get("/abonnement").with(somMeg()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("niva", org.hamcrest.Matchers.hasSize(7)))
                // Alle prisene skal stå på siden
                .andExpect(content().string(containsString("30 oppskrifter")))
                .andExpect(content().string(containsString("200 oppskrifter")))
                .andExpect(content().string(containsString("Ubegrenset")));
    }

    @Test
    void nivaerUtenPris_visesSomKommer() throws Exception {
        bruker(Abonnement.GRATIS, false);
        when(oppskriftRepository.countByBrukerId(BRUKER_ID)).thenReturn(0L);

        mockMvc.perform(get("/abonnement").with(somMeg()))
                .andExpect(status().isOk())
                // 30 og 150 er konfigurert, de andre ikke
                .andExpect(model().attribute("tilgjengelig",
                        org.hamcrest.Matchers.hasEntry("NIVA_30", true)))
                .andExpect(model().attribute("tilgjengelig",
                        org.hamcrest.Matchers.hasEntry("NIVA_80", false)))
                .andExpect(content().string(containsString("Kommer")));
    }

    @Test
    void anbefaltPlan_folgerAntallOppskrifter() throws Exception {
        bruker(Abonnement.GRATIS, false);
        when(oppskriftRepository.countByBrukerId(BRUKER_ID)).thenReturn(60L);

        mockMvc.perform(get("/abonnement").with(somMeg()))
                .andExpect(status().isOk())
                // 60 oppskrifter krever minst 80-nivået
                .andExpect(model().attribute("anbefalt", Abonnement.NIVA_80));
    }

    @Test
    void abonnent_serSinEgenPlanOgKanBytte() throws Exception {
        bruker(Abonnement.NIVA_150, true);
        when(oppskriftRepository.countByBrukerId(BRUKER_ID)).thenReturn(120L);

        mockMvc.perform(get("/abonnement").with(somMeg()))
                .andExpect(status().isOk())
                .andExpect(model().attribute("plan", Abonnement.NIVA_150))
                .andExpect(content().string(containsString("150 oppskrifter")))
                .andExpect(content().string(containsString("Bytt plan")));
    }

    // ── CHECKOUT ──────────────────────────────────────────────────

    @Test
    void checkout_utenNiva_avvises() throws Exception {
        bruker(Abonnement.GRATIS, false);

        mockMvc.perform(post("/abonnement/checkout").with(somMeg()).with(csrf()))
                .andExpect(redirectedUrl("/abonnement?feil=ugyldig-niva"));
    }

    @Test
    void checkout_nivaUtenKonfigurertPris_girFeil() throws Exception {
        bruker(Abonnement.GRATIS, false);

        // 80-nivået har ingen price-ID i denne testen
        mockMvc.perform(post("/abonnement/checkout")
                        .param("niva", "NIVA_80").with(somMeg()).with(csrf()))
                .andExpect(redirectedUrl("/abonnement?feil=stripe-ikke-konfigurert"));
    }

    // ── GRENSEN HÅNDHEVES ─────────────────────────────────────────

    @Test
    void lagre_stoppesNarPlanenErFull() throws Exception {
        bruker(Abonnement.NIVA_30, true);
        when(oppskriftRepository.countByBrukerId(BRUKER_ID)).thenReturn(30L);

        mockMvc.perform(post("/lagre")
                        .param("tittel", "Ny oppskrift")
                        .with(somMeg()).with(csrf()))
                .andExpect(redirectedUrl("/abonnement?grense=true"));
    }

    @Test
    void lagre_slipperGjennomNarDetErPlass() throws Exception {
        bruker(Abonnement.NIVA_30, true);
        when(oppskriftRepository.countByBrukerId(BRUKER_ID)).thenReturn(29L);

        mockMvc.perform(post("/lagre")
                        .param("tittel", "Ny oppskrift")
                        .with(somMeg()).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/kokebok"));
    }

    @Test
    void lagre_ubegrensetStoppesAldri() throws Exception {
        bruker(Abonnement.UBEGRENSET, true);
        when(oppskriftRepository.countByBrukerId(BRUKER_ID)).thenReturn(4000L);

        mockMvc.perform(post("/lagre")
                        .param("tittel", "Ny oppskrift")
                        .with(somMeg()).with(csrf()))
                .andExpect(redirectedUrl("/kokebok"));
    }

    @Test
    void lagre_oppsagtAbonnentFallerTilGratisgrensen() throws Exception {
        bruker(Abonnement.UBEGRENSET, false);        // hadde ubegrenset, men sa opp
        when(oppskriftRepository.countByBrukerId(BRUKER_ID)).thenReturn(12L);

        mockMvc.perform(post("/lagre")
                        .param("tittel", "Ny oppskrift")
                        .with(somMeg()).with(csrf()))
                .andExpect(redirectedUrl("/abonnement?grense=true"));
    }
}
