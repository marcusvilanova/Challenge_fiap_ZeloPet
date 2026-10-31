package br.com.zelo.repo;

import br.com.zelo.domain.Triage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface TriageRepository extends JpaRepository<Triage, Long> {

    List<Triage> findByPetIdOrderByCreatedAtDesc(Long petId, Pageable pageable);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update Triage t set t.sharedWithClinic = false where t.pet.id = :petId")
    int unshareAllOf(@Param("petId") Long petId);

    Optional<Triage> findByIdAndPetOwnerId(Long id, Long ownerId);

    /** Triagens compartilhadas cujo consentimento continua ativo (revogar o consentimento as oculta). */
    @Query("""
            select t from Triage t
            where t.sharedWithClinic = true
              and exists (select c.id from Consent c where c.pet.id = t.pet.id and c.revokedAt is null)
            order by t.createdAt desc
            """)
    List<Triage> findVisibleToClinic(Pageable pageable);

    @Query("""
            select t from Triage t
            where t.id = :id and t.sharedWithClinic = true
              and exists (select c.id from Consent c where c.pet.id = t.pet.id and c.revokedAt is null)
            """)
    Optional<Triage> findVisibleToClinicById(@Param("id") Long id);
}
