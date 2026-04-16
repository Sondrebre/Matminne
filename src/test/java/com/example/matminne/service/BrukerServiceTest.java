package com.example.matminne.service;

import com.example.matminne.model.Bruker;
import com.example.matminne.repository.BrukerRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BrukerServiceTest {

    @Mock
    private BrukerRepository brukerRepository;

    @InjectMocks
    private BrukerService brukerService;

    @Test
    void finnVedEpost_returnererBruker() {
        Bruker bruker = new Bruker("Ola Nordmann", "ola@example.com");
        when(brukerRepository.findByEpost("ola@example.com")).thenReturn(bruker);

        Bruker resultat = brukerService.finnVedEpost("ola@example.com");

        assertEquals("Ola Nordmann", resultat.getFulltNavn());
        verify(brukerRepository).findByEpost("ola@example.com");
    }

    @Test
    void finnVedEpost_returnererNullNaarIkkeFunnet() {
        when(brukerRepository.findByEpost("ukjent@example.com")).thenReturn(null);

        Bruker resultat = brukerService.finnVedEpost("ukjent@example.com");

        assertNull(resultat);
    }

    @Test
    void lagreBruker_kallerRepository() {
        Bruker bruker = new Bruker("Kari", "kari@example.com");

        brukerService.lagreBruker(bruker);

        verify(brukerRepository).save(bruker);
    }

    @Test
    void findById_returnererBruker() {
        Bruker bruker = new Bruker("Per", "per@example.com");
        bruker.setId(1L);
        when(brukerRepository.findById(1L)).thenReturn(Optional.of(bruker));

        Bruker resultat = brukerService.findById(1L);

        assertNotNull(resultat);
        assertEquals("Per", resultat.getFulltNavn());
    }

    @Test
    void findById_returnererNullNaarIkkeFunnet() {
        when(brukerRepository.findById(99L)).thenReturn(Optional.empty());

        Bruker resultat = brukerService.findById(99L);

        assertNull(resultat);
    }

    @Test
    void sokEtterNavn_returnererListe() {
        List<Bruker> liste = List.of(
            new Bruker("Ola Hansen", "ola@example.com"),
            new Bruker("Ola Berg", "olab@example.com")
        );
        when(brukerRepository.findByFulltNavnContainingIgnoreCase("Ola")).thenReturn(liste);

        List<Bruker> resultat = brukerService.sokEtterNavn("Ola");

        assertEquals(2, resultat.size());
    }
}
