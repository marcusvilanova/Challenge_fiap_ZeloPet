package br.com.zelo.api;

import br.com.zelo.api.dto.ApiDtos.ChatHistory;
import br.com.zelo.api.dto.ApiDtos.ChatReply;
import br.com.zelo.api.dto.ApiDtos.ChatRequest;
import br.com.zelo.api.dto.ApiDtos.TriageRequest;
import br.com.zelo.api.dto.TriageResult;
import br.com.zelo.security.AppUserDetails;
import br.com.zelo.service.ChatService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
@Tag(name = "Chatbot e triagem")
public class ChatController {

    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    public record OpenSessionRequest(@NotBlank String petId, boolean fresh) {
    }

    @Operation(summary = "Abre (ou retoma) a conversa de um pet; fresh=true começa uma conversa nova")
    @PostMapping("/chat/sessions")
    public ChatHistory open(@AuthenticationPrincipal AppUserDetails me, @Valid @RequestBody OpenSessionRequest request) {
        return chat.open(me, request.petId(), request.fresh());
    }

    @Operation(summary = "Envia uma mensagem ao chatbot e recebe a resposta (com a triagem estruturada, quando houver)")
    @PostMapping("/chat/messages")
    public ChatReply send(@AuthenticationPrincipal AppUserDetails me, @Valid @RequestBody ChatRequest request) {
        return chat.handle(me, request);
    }

    @Operation(summary = "Histórico de mensagens de uma conversa")
    @GetMapping("/chat/sessions/{sessionId}/messages")
    public ChatHistory history(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long sessionId) {
        return chat.history(me, sessionId);
    }

    @Operation(summary = "Triagem avulsa (sem conversa): relato em texto livre -> saída estruturada (contrato Sprint 3)")
    @PostMapping("/triage")
    public TriageResult triage(@AuthenticationPrincipal AppUserDetails me, @Valid @RequestBody TriageRequest request) {
        return chat.triageOnce(me, request);
    }
}
