package com.example.matminne;

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

    public List<Bruker> sokEtterNavn(String navn) {
        return brukerRepository.findByFulltNavnContainingIgnoreCase(navn);
    }
}
