package br.com.zelo.repo;

import br.com.zelo.domain.TimelineEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;

public interface TimelineEventRepository extends JpaRepository<TimelineEvent, Long> {

    List<TimelineEvent> findByPetIdOrderByOccurredAtDescIdDesc(Long petId, Pageable pageable);

    List<TimelineEvent> findByPetIdAndTypeInOrderByOccurredAtDescIdDesc(Long petId, Collection<String> types,
                                                                        Pageable pageable);
}
