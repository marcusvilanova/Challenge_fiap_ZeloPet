package br.com.zelo.repo;

import br.com.zelo.domain.ClinicReview;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

public interface ClinicReviewRepository extends JpaRepository<ClinicReview, Long> {

    @Query("select distinct r.triage.id from ClinicReview r where r.triage.id in :ids")
    Set<Long> findReviewedTriageIds(@Param("ids") Collection<Long> ids);

    List<ClinicReview> findByTriageIdOrderByCreatedAtDesc(Long triageId);

    Optional<ClinicReview> findFirstByTriageIdOrderByCreatedAtDesc(Long triageId);
}
