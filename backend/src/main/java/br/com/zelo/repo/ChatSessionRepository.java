package br.com.zelo.repo;

import br.com.zelo.domain.ChatSession;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ChatSessionRepository extends JpaRepository<ChatSession, Long> {

    Optional<ChatSession> findByIdAndUserId(Long id, Long userId);

    Optional<ChatSession> findFirstByPetIdAndUserIdOrderByUpdatedAtDesc(Long petId, Long userId);
}
