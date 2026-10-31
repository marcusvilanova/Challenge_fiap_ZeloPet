package br.com.zelo.repo;

import br.com.zelo.domain.Consent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ConsentRepository extends JpaRepository<Consent, Long> {

    Optional<Consent> findFirstByPetIdAndScopeAndRevokedAtIsNull(Long petId, String scope);

    List<Consent> findByPetIdOrderByGrantedAtDesc(Long petId);
}
