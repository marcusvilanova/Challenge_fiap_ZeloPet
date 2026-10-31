package br.com.zelo.api;

import br.com.zelo.api.dto.ApiDtos.ConsentRequest;
import br.com.zelo.api.dto.ApiDtos.ConsentResponse;
import br.com.zelo.security.AppUserDetails;
import br.com.zelo.service.ConsentService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/sharing/consents")
@Tag(name = "Consentimento (LGPD)")
public class SharingController {

    private final ConsentService consents;

    public SharingController(ConsentService consents) {
        this.consents = consents;
    }

    @Operation(summary = "Autoriza o compartilhamento de triagens do pet com a clínica")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ConsentResponse grant(@AuthenticationPrincipal AppUserDetails me, @Valid @RequestBody ConsentRequest request) {
        return consents.grant(me.id(), request.petId(), request.scope());
    }

    @Operation(summary = "Consentimentos do pet (ativos e revogados)")
    @GetMapping
    public List<ConsentResponse> list(@AuthenticationPrincipal AppUserDetails me, @RequestParam("pet_id") String petId) {
        return consents.list(me.id(), petId);
    }

    @Operation(summary = "Revoga o consentimento: a clínica deixa de ver as triagens do pet")
    @DeleteMapping("/{consentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void revoke(@AuthenticationPrincipal AppUserDetails me, @PathVariable Long consentId) {
        consents.revoke(me.id(), consentId);
    }
}
