package com.example.matminne.service;

import com.example.matminne.model.*;
import com.example.matminne.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

@Service
public class BrukerService {

    @Autowired private BrukerRepository brukerRepository;
    @Autowired private OppskriftRepository oppskriftRepository;
    @Autowired private HandelListeRepository handelListeRepository;
    @Autowired private VennskapRepository vennskapRepository;
    @Autowired private VarselRepository varselRepository;
    @Autowired private UkesmenyRepository ukesmenyRepository;
    @Autowired private SamlingRepository samlingRepository;
    @Autowired private SamlingOppskriftRepository samlingOppskriftRepository;
    @Autowired private LikeRepository likeRepository;

    public void lagreBruker(Bruker bruker) {
        brukerRepository.save(bruker);
    }

    public Bruker finnVedEpost(String epost) {
        return brukerRepository.findByEpost(epost);
    }

    public Bruker findById(Long id) {
        return brukerRepository.findById(id).orElse(null);
    }

    public List<Bruker> sokEtterNavn(String navn) {
        return brukerRepository.findByFulltNavnContainingIgnoreCase(navn);
    }

    @Transactional
    public void slettBruker(Long brukerId) {
        // Samlinger
        List<Samling> samlinger = samlingRepository.findByBrukerIdOrderByOpprettetDesc(brukerId);
        for (Samling s : samlinger) {
            samlingOppskriftRepository.deleteAll(samlingOppskriftRepository.findBySamlingId(s.getId()));
        }
        samlingRepository.deleteAll(samlinger);
        // Likes this user gave
        likeRepository.deleteByBrukerId(brukerId);
        // Handleliste
        handelListeRepository.deleteByBrukerId(brukerId);
        // Vennskap
        vennskapRepository.deleteAll(vennskapRepository.findByBrukerId(brukerId));
        vennskapRepository.deleteAll(vennskapRepository.findByVennId(brukerId));
        // Varsler
        varselRepository.deleteAll(varselRepository.findByMottakerBrukerIdOrderByOpprettetDesc(brukerId));
        // Ukesmeny
        ukesmenyRepository.deleteAll(ukesmenyRepository.findByBrukerId(brukerId));
        // Oppskrifter (likes/comments on these by others are orphaned — acceptable)
        oppskriftRepository.deleteAll(oppskriftRepository.findByBrukerId(brukerId));
        // User
        brukerRepository.deleteById(brukerId);
    }
}
