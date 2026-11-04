package br.com.zelo.repo;

import br.com.zelo.domain.ChatMessage;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ChatMessageRepository extends JpaRepository<ChatMessage, Long> {

    /** Mensagens mais recentes primeiro; o chamador inverte para exibir em ordem cronológica. */
    List<ChatMessage> findBySessionIdOrderByIdDesc(Long sessionId, Pageable pageable);

    /** Conversa completa em ordem cronológica (usada para mostrar à clínica o diálogo que gerou a triagem). */
    List<ChatMessage> findBySessionIdOrderByIdAsc(Long sessionId);
}
