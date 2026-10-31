package br.com.zelo.api;

import br.com.zelo.api.dto.ApiDtos.ClinicTriageDetail;
import br.com.zelo.api.dto.ApiDtos.ClinicTriageSummary;
import br.com.zelo.api.dto.ApiDtos.ReviewRequest;
import br.com.zelo.api.dto.ApiDtos.ReviewResponse;
import br.com.zelo.security.AppUserDetails;
import br.com.zelo.service.ClinicService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Acesso restrito ao perfil CLINICA (ver SecurityConfig). */
@RestController
@RequestMapping("/api/clinic")
@Tag(name = "Painel da clínica")
public class ClinicController {

    private final ClinicService clinic;

    public ClinicController(ClinicService clinic) {
        this.clinic = clinic;
    }

    @Operation(summary = "Fila de triagens compartilhadas com consentimento ativo (maior prioridade primeiro)")
    @GetMapping("/triages")
    public List<ClinicTriageSummary> queue() {
        return clinic.queue();
    }

    @Operation(summary = "Detalhe de uma triagem compartilhada, com perfil do pet e linha do tempo")
    @GetMapping("/triages/{triageId}")
    public ClinicTriageDetail detail(@PathVariable String triageId) {
        return clinic.detail(triageId);
    }

    @Operation(summary = "Registra a revisão profissional (confirma ou ajusta a prioridade sugerida pela IA)")
    @PostMapping("/reviews")
    @ResponseStatus(HttpStatus.CREATED)
    public ReviewResponse review(@AuthenticationPrincipal AppUserDetails me, @Valid @RequestBody ReviewRequest request) {
        return clinic.review(me.id(), request);
    }
}
