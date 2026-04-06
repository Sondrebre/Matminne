package com.example.matminne.service;

import com.example.matminne.model.*;
import com.example.matminne.repository.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class BrukerService {

    @Autowired
    private BrukerRepository brukerRepository;

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
}
