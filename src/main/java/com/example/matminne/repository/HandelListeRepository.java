package com.example.matminne.repository;

import com.example.matminne.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;

public interface HandelListeRepository extends JpaRepository<HandelListeItem, Long> {
    List<HandelListeItem> findByBrukerIdOrderByFerdigAscOpprettetDesc(Long brukerId);
    @Transactional
    void deleteByBrukerIdAndFerdigTrue(Long brukerId);
    @Transactional
    void deleteByBrukerId(Long brukerId);
}
