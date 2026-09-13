package com.example.matminne.controller;

import com.example.matminne.model.Bruker;
import com.example.matminne.model.Oppskrift;
import com.example.matminne.model.Samling;
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
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integrasjonstester for betalte samlinger: at malene faktisk renderer,
 * at låst innhold er låst, og at det ikke lekker ut via andre endepunkter.
 */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = {
        "app.skapere=skaper@test.no",
        "app.plattform.andel=20"
})
class BetaltSamlingIntegrationTest {

    private static final long SKAPER_ID  = 1L;
    private static final long KJOPER_ID  = 2L;
    private static final long SAMLING_ID = 10L;
    private static final long OPPSKRIFT_ID = 100L;

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
        when(samlingRepository.finnTilSalgsAvSkaper(anyLong())).thenReturn(Collections.emptyList());
    }

    // ── HJELPERE ──────────────────────────────────────────────────

    private Bruker bruker(long id, String epost) {
        Bruker b = new Bruker("Test " + epost, epost);
        b.setId(id);
        return b;
    }

    private Bruker skaperKlarTilSalg() {
        Bruker b = bruker(SKAPER_ID, "skaper@test.no");
        b.setStripeConnectId("acct_test123");
        b.setConnectKlar(true);
        return b;
    }

    private Samling betaltSamling() {
        Samling s = new Samling();
        s.setId(SAMLING_ID);
        s.setBrukerId(SKAPER_ID);
        s.setNavn("Mine beste middager");
        s.setBeskrivelse("20 oppskrifter");
        s.setPris(14900);
        s.setErPublisert(true);
        return s;
    }

    private Oppskrift laastOppskrift() {
        Oppskrift o = new Oppskrift();
        o.setId(OPPSKRIFT_ID);
        o.setBrukerId(SKAPER_ID);
        o.setBrukerEpost("skaper@test.no");
        o.setBrukerNavn("Skaper");
        o.setTittel("Hemmelig lasagne");
        o.setIngredienser("500g kjøttdeig\n1 løk");
        o.setFremgangsmate("Stek kjøttdeigen.");
        o.setErOffentlig(true);
        return o;
    }

    private static org.springframework.test.web.servlet.request.RequestPostProcessor somBruker(String epost) {
        return oauth2Login().attributes(a -> {
            a.put("email", epost);
            a.put("name", "Test");
        });
    }

    /** Oppskriften ligger i en publisert betalt samling. */
    private void gjorOppskriftLaast() {
        when(samlingRepository.finnBetalteSomInneholder(OPPSKRIFT_ID))
                .thenReturn(List.of(betaltSamling()));
        when(samlingRepository.findById(SAMLING_ID)).thenReturn(Optional.of(betaltSamling()));
        when(oppskriftRepository.findById(OPPSKRIFT_ID)).thenReturn(Optional.of(laastOppskrift()));
        when(brukerService.findById(SKAPER_ID)).thenReturn(skaperKlarTilSalg());
        when(likeRepository.countByOppskriftId(anyLong())).thenReturn(0L);
        when(kommentarRepository.findByOppskriftIdOrderByOpprettetAsc(anyLong()))
                .thenReturn(Collections.emptyList());
        when(ratingRepository.snitRatingForOppskrift(anyLong())).thenReturn(0.0);
        when(ratingRepository.countByOppskriftId(anyLong())).thenReturn(0L);
    }

    // ── LÅSING PÅ DETALJSIDEN ─────────────────────────────────────

    @Test
    void detaljer_laastOppskrift_viserKjopsoppfordringIkkeInnhold() throws Exception {
        gjorOppskriftLaast();
        Bruker kjoper = bruker(KJOPER_ID, "kjoper@test.no");
        when(brukerService.finnVedEpost("kjoper@test.no")).thenReturn(kjoper);
        when(kjopRepository.existsByBrukerIdAndSamlingId(KJOPER_ID, SAMLING_ID)).thenReturn(false);

        mockMvc.perform(get("/detaljer/" + OPPSKRIFT_ID).with(somBruker("kjoper@test.no")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("erLast", true))
                // Kjøpsoppfordringen vises
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Lås opp for")))
                // Selve oppskriften gjør det ikke
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("Stek kjøttdeigen"))))
                .andExpect(content().string(
                        org.hamcrest.Matchers.not(org.hamcrest.Matchers.containsString("500g kjøttdeig"))));
    }

    @Test
    void detaljer_etterKjop_viserInnholdet() throws Exception {
        gjorOppskriftLaast();
        Bruker kjoper = bruker(KJOPER_ID, "kjoper@test.no");
        when(brukerService.finnVedEpost("kjoper@test.no")).thenReturn(kjoper);
        when(kjopRepository.existsByBrukerIdAndSamlingId(KJOPER_ID, SAMLING_ID)).thenReturn(true);

        mockMvc.perform(get("/detaljer/" + OPPSKRIFT_ID).with(somBruker("kjoper@test.no")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("erLast", false))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Stek kjøttdeigen")));
    }

    @Test
    void detaljer_eierenSerAlltidSittEgetInnhold() throws Exception {
        gjorOppskriftLaast();
        when(brukerService.finnVedEpost("skaper@test.no")).thenReturn(skaperKlarTilSalg());

        mockMvc.perform(get("/detaljer/" + OPPSKRIFT_ID).with(somBruker("skaper@test.no")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("erLast", false))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Stek kjøttdeigen")));
    }

    // ── LEKKASJER ─────────────────────────────────────────────────

    @Test
    void pdf_laastOppskrift_gir403() throws Exception {
        gjorOppskriftLaast();
        Bruker kjoper = bruker(KJOPER_ID, "kjoper@test.no");
        when(brukerService.finnVedEpost("kjoper@test.no")).thenReturn(kjoper);
        when(kjopRepository.existsByBrukerIdAndSamlingId(KJOPER_ID, SAMLING_ID)).thenReturn(false);

        mockMvc.perform(post("/pdf").param("id", String.valueOf(OPPSKRIFT_ID))
                        .with(somBruker("kjoper@test.no")).with(csrf()))
                .andExpect(status().isForbidden());
    }

    @Test
    void kopier_laastOppskrift_kopiererIkke() throws Exception {
        gjorOppskriftLaast();
        Bruker kjoper = bruker(KJOPER_ID, "kjoper@test.no");
        when(brukerService.finnVedEpost("kjoper@test.no")).thenReturn(kjoper);
        when(kjopRepository.existsByBrukerIdAndSamlingId(KJOPER_ID, SAMLING_ID)).thenReturn(false);

        mockMvc.perform(post("/kopier/" + OPPSKRIFT_ID)
                        .with(somBruker("kjoper@test.no")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        // Ingen kopi skal ha blitt lagret
        verify(oppskriftRepository, never()).save(any(Oppskrift.class));
    }

    @Test
    void handleliste_laastOppskrift_leggerIkkeTilIngredienser() throws Exception {
        gjorOppskriftLaast();
        Bruker kjoper = bruker(KJOPER_ID, "kjoper@test.no");
        when(brukerService.finnVedEpost("kjoper@test.no")).thenReturn(kjoper);
        when(kjopRepository.existsByBrukerIdAndSamlingId(KJOPER_ID, SAMLING_ID)).thenReturn(false);

        mockMvc.perform(post("/handleliste/fra-oppskrift/" + OPPSKRIFT_ID)
                        .with(somBruker("kjoper@test.no")).with(csrf()))
                .andExpect(status().is3xxRedirection());

        verify(handelListeRepository, never()).save(any());
    }

    // ── SALGSSIDEN ────────────────────────────────────────────────

    @Test
    void salgsside_renderer_medKjopsknapp() throws Exception {
        when(samlingRepository.findById(SAMLING_ID)).thenReturn(Optional.of(betaltSamling()));
        when(brukerService.findById(SKAPER_ID)).thenReturn(skaperKlarTilSalg());
        when(samlingOppskriftRepository.findBySamlingId(SAMLING_ID)).thenReturn(Collections.emptyList());
        Bruker kjoper = bruker(KJOPER_ID, "kjoper@test.no");
        when(brukerService.finnVedEpost("kjoper@test.no")).thenReturn(kjoper);
        when(kjopRepository.existsByBrukerIdAndSamlingId(KJOPER_ID, SAMLING_ID)).thenReturn(false);

        mockMvc.perform(get("/samling/" + SAMLING_ID).with(somBruker("kjoper@test.no")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("harTilgang", false))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("149 kr")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Kjøp og lås opp")));
    }

    @Test
    void salgsside_upublisert_erSkjultForAndre() throws Exception {
        Samling utkast = betaltSamling();
        utkast.setErPublisert(false);
        when(samlingRepository.findById(SAMLING_ID)).thenReturn(Optional.of(utkast));
        Bruker kjoper = bruker(KJOPER_ID, "kjoper@test.no");
        when(brukerService.finnVedEpost("kjoper@test.no")).thenReturn(kjoper);

        mockMvc.perform(get("/samling/" + SAMLING_ID).with(somBruker("kjoper@test.no")))
                .andExpect(status().is3xxRedirection());
    }

    // ── SKAPERSTUDIO ──────────────────────────────────────────────

    @Test
    void skaperstudio_renderer_forInvitertSkaper() throws Exception {
        when(brukerService.finnVedEpost("skaper@test.no")).thenReturn(skaperKlarTilSalg());
        when(samlingRepository.finnBetalteAvSkaper(SKAPER_ID)).thenReturn(List.of(betaltSamling()));
        when(samlingOppskriftRepository.countBySamlingId(SAMLING_ID)).thenReturn(5L);
        when(kjopRepository.countBySamlingId(SAMLING_ID)).thenReturn(3L);
        when(kjopRepository.findBySkaperIdOrderByDatoKjoptDesc(SKAPER_ID))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/skaper").with(somBruker("skaper@test.no")))
                .andExpect(status().isOk())
                .andExpect(model().attribute("kanSelge", true))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Mine beste middager")));
    }

    @Test
    void skaperstudio_erStengtForVanligeBrukere() throws Exception {
        Bruker vanlig = bruker(KJOPER_ID, "kjoper@test.no");
        when(brukerService.finnVedEpost("kjoper@test.no")).thenReturn(vanlig);

        mockMvc.perform(get("/skaper").with(somBruker("kjoper@test.no")))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/kokebok"));
    }

    @Test
    void publisering_krevesFullfortStripeOppsett() throws Exception {
        Bruker utenConnect = bruker(SKAPER_ID, "skaper@test.no");   // connectKlar = false
        when(brukerService.finnVedEpost("skaper@test.no")).thenReturn(utenConnect);
        Samling utkast = betaltSamling();
        utkast.setErPublisert(false);
        when(samlingRepository.findById(SAMLING_ID)).thenReturn(Optional.of(utkast));

        mockMvc.perform(post("/skaper/samling/" + SAMLING_ID + "/publiser")
                        .with(somBruker("skaper@test.no")).with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/skaper/samling/" + SAMLING_ID + "?feil=ikke-klar"));

        verify(samlingRepository, never()).save(any(Samling.class));
    }

    // ── MINE KJØP ─────────────────────────────────────────────────

    @Test
    void mineKjop_renderer() throws Exception {
        Bruker kjoper = bruker(KJOPER_ID, "kjoper@test.no");
        when(brukerService.finnVedEpost("kjoper@test.no")).thenReturn(kjoper);
        when(kjopRepository.findByBrukerIdOrderByDatoKjoptDesc(KJOPER_ID))
                .thenReturn(Collections.emptyList());

        mockMvc.perform(get("/mine-kjop").with(somBruker("kjoper@test.no")))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Mine kjøp")));
    }
}
