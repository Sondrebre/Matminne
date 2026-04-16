package com.example.matminne.controller;

import com.example.matminne.model.Bruker;
import com.example.matminne.model.Oppskrift;
import com.example.matminne.repository.*;
import com.example.matminne.service.AiOppskriftService;
import com.example.matminne.service.BrukerService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.Collections;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Integrasjonstester for WebController.
 * Tester HTTP-lag med Spring Security og Thymeleaf.
 */
@SpringBootTest
@ActiveProfiles("test")
class WebControllerIntegrationTest {

    @Autowired
    private WebApplicationContext wac;

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

    @BeforeEach
    void setup() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(wac)
                .apply(SecurityMockMvcConfigurers.springSecurity())
                .build();
    }

    // ── UNIT: Uautentiserte brukere ──────────────────────────────────────────

    @Test
    void forsiden_uautentisert_viserHjem() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk());
    }

    @Test
    void kokebok_uautentisert_redirecterTilHjem() throws Exception {
        mockMvc.perform(get("/kokebok"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void oppdag_uautentisert_redirecterTilLogin() throws Exception {
        mockMvc.perform(get("/oppdag"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void profil_uautentisert_redirecterTilLogin() throws Exception {
        mockMvc.perform(get("/profil/test@test.no"))
                .andExpect(status().is3xxRedirection());
    }

    // ── INTEGRASJON: Autentiserte brukere ────────────────────────────────────

    @Test
    void kokebok_autentisert_returnerOk() throws Exception {
        Bruker bruker = brukerMedEpost("test@test.no");
        when(brukerService.finnVedEpost("test@test.no")).thenReturn(bruker);
        when(oppskriftRepository.findByBrukerId(anyLong())).thenReturn(Collections.emptyList());
        when(varselRepository.countByMottakerBrukerIdAndLestFalse(anyLong())).thenReturn(0L);

        mockMvc.perform(get("/kokebok").with(oauth2Login().attributes(a -> {
            a.put("email", "test@test.no");
            a.put("name", "Test Bruker");
        })))
                .andExpect(status().isOk());
    }

    @Test
    void oppdag_autentisert_returnerOk() throws Exception {
        Bruker bruker = brukerMedEpost("test@test.no");
        when(brukerService.finnVedEpost("test@test.no")).thenReturn(bruker);
        when(oppskriftRepository.findByErOffentligTrueOrderByIdDesc()).thenReturn(Collections.emptyList());
        when(likeRepository.findTopLikedOppskriftIds(any())).thenReturn(Collections.emptyList());
        when(varselRepository.countByMottakerBrukerIdAndLestFalse(anyLong())).thenReturn(0L);

        mockMvc.perform(get("/oppdag").with(oauth2Login().attributes(a -> {
            a.put("email", "test@test.no");
            a.put("name", "Test Bruker");
        })))
                .andExpect(status().isOk());
    }

    @Test
    void lagreOppskrift_autentisert_redirecterTilKokebok() throws Exception {
        Bruker bruker = brukerMedEpost("test@test.no");
        when(brukerService.finnVedEpost("test@test.no")).thenReturn(bruker);
        when(varselRepository.countByMottakerBrukerIdAndLestFalse(anyLong())).thenReturn(0L);

        mockMvc.perform(post("/lagre")
                .with(csrf())
                .with(oauth2Login().attributes(a -> {
                    a.put("email", "test@test.no");
                    a.put("name", "Test Bruker");
                }))
                .param("tittel", "Testoppskrift")
                .param("ingredienser", "200g pasta")
                .param("fremgangsmate", "Kok pastaen")
                .param("erOffentlig", "false"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/kokebok"));
    }

    @Test
    void lagreOppskrift_utenCsrf_gir403() throws Exception {
        mockMvc.perform(post("/lagre")
                .with(oauth2Login().attributes(a -> {
                    a.put("email", "test@test.no");
                    a.put("name", "Test Bruker");
                }))
                .param("tittel", "Testoppskrift"))
                .andExpect(status().isForbidden());
    }

    // ── INTEGRASJON: Oppskrift-detaljer ──────────────────────────────────────

    @Test
    void detaljer_offentligOppskrift_uautentisert_redirecterTilLogin() throws Exception {
        mockMvc.perform(get("/detaljer/1"))
                .andExpect(status().is3xxRedirection());
    }

    @Test
    void detaljer_ikkeEksisterendeOppskrift_autentisert_returnerSide() throws Exception {
        Bruker bruker = brukerMedEpost("test@test.no");
        when(brukerService.finnVedEpost("test@test.no")).thenReturn(bruker);
        when(oppskriftRepository.findById(anyLong())).thenReturn(Optional.empty());
        when(varselRepository.countByMottakerBrukerIdAndLestFalse(anyLong())).thenReturn(0L);

        mockMvc.perform(get("/detaljer/999")
                .with(oauth2Login().attributes(a -> {
                    a.put("email", "test@test.no");
                    a.put("name", "Test Bruker");
                })))
                .andExpect(status().isOk());
    }

    @Test
    void detaljer_privatOppskriftAvAnnenBruker_redirecterTilOppdag() throws Exception {
        Bruker bruker = brukerMedEpost("test@test.no");
        when(brukerService.finnVedEpost("test@test.no")).thenReturn(bruker);
        when(varselRepository.countByMottakerBrukerIdAndLestFalse(anyLong())).thenReturn(0L);

        Oppskrift privat = new Oppskrift();
        privat.setId(42L);
        privat.setErOffentlig(false);
        privat.setBrukerEpost("annenbruker@test.no");
        when(oppskriftRepository.findById(42L)).thenReturn(Optional.of(privat));

        mockMvc.perform(get("/detaljer/42")
                .with(oauth2Login().attributes(a -> {
                    a.put("email", "test@test.no");
                    a.put("name", "Test Bruker");
                })))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/oppdag"));
    }

    // ── HJELPEMETODER ────────────────────────────────────────────────────────

    private Bruker brukerMedEpost(String epost) {
        Bruker b = new Bruker("Test Bruker", epost);
        b.setId(1L);
        return b;
    }
}
