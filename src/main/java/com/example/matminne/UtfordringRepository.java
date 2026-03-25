package com.example.matminne;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface UtfordringRepository extends JpaRepository<Utfordring, Long> {
    List<Utfordring> findByAktivTrueOrderByOpprettetDesc();
}
