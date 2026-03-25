package com.example.matminne;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface KommentarRepository extends JpaRepository<Kommentar, Long> {
    List<Kommentar> findByOppskriftIdOrderByOpprettetAsc(Long oppskriftId);
}
