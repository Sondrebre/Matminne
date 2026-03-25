package com.example.matminne;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface VennskapRepository extends JpaRepository<Vennskap, Long> {

    // VIKTIG: Pass på at det står "Id" her, ikke "Epost"
    List<Vennskap> findByBrukerId(Long brukerId);

    // VIKTIG: Pass på at det står "Id" på begge her også
    Vennskap findByBrukerIdAndVennId(Long brukerId, Long vennId);

    boolean existsByBrukerIdAndVennId(Long brukerId, Long vennId);

    long countByVennId(Long vennId);
}