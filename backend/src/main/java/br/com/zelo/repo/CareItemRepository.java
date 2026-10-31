package br.com.zelo.repo;

import br.com.zelo.domain.CareItem;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CareItemRepository extends JpaRepository<CareItem, Long> {

    List<CareItem> findByPetIdOrderByNextDueAsc(Long petId);
}
