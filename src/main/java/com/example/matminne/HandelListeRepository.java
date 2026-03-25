package com.example.matminne;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface HandelListeRepository extends JpaRepository<HandelListeItem, Long> {
    List<HandelListeItem> findByBrukerIdOrderByFerdigAscOpprettetDesc(Long brukerId);
    void deleteByBrukerIdAndFerdigTrue(Long brukerId);
}
