package com.example.matminne;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface SamlingRepository extends JpaRepository<Samling, Long> {
    List<Samling> findByBrukerIdOrderByOpprettetDesc(Long brukerId);
}
