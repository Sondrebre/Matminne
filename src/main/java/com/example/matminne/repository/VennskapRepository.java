package com.example.matminne.repository;

import com.example.matminne.model.*;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VennskapRepository extends JpaRepository<Vennskap, Long> {

    List<Vennskap> findByBrukerId(Long brukerId);

    List<Vennskap> findByVennId(Long vennId);

    Vennskap findByBrukerIdAndVennId(Long brukerId, Long vennId);

    boolean existsByBrukerIdAndVennId(Long brukerId, Long vennId);

    long countByVennId(Long vennId);
}