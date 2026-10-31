package br.com.zelo.service;

import br.com.zelo.api.dto.ApiDtos.ConsentResponse;
import br.com.zelo.domain.AppUser;
import br.com.zelo.domain.Consent;
import br.com.zelo.domain.Pet;
import br.com.zelo.repo.AppUserRepository;
import br.com.zelo.repo.ConsentRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/**
 * Consentimento de compartilhamento com a clínica (LGPD). O escopo e a versão do termo ficam registrados e
 * o tutor pode revogar a qualquer momento — revogar oculta as triagens compartilhadas do painel da clínica.
 */
@Service
public class ConsentService {

    private final ConsentRepository consents;
    private final AppUserRepository users;
    private final PetService pets;
    private final TriageService triages;
    private final TimelineService timeline;
    private final Clock clock;

    public ConsentService(ConsentRepository consents, AppUserRepository users, PetService pets, TriageService triages,
                          TimelineService timeline, Clock clock) {
        this.consents = consents;
        this.users = users;
        this.pets = pets;
        this.triages = triages;
        this.timeline = timeline;
        this.clock = clock;
    }

    @Transactional
    public ConsentResponse grant(Long userId, String petPublicId, String scope) {
        return toResponse(grant(userId, pets.requireOwned(userId, petPublicId), scope));
    }

    /** Idempotente: se já existe consentimento ativo para o escopo, devolve o mesmo. */
    @Transactional
    public Consent grant(Long userId, Pet pet, String scope) {
        String effectiveScope = scope == null || scope.isBlank() ? Consent.SCOPE_TRIAGE_AND_CLINIC : scope;
        if (!Consent.SCOPE_TRIAGE_AND_CLINIC.equals(effectiveScope)) {
            throw new BadRequestException("Escopo de compartilhamento não suportado");
        }
        return consents.findFirstByPetIdAndScopeAndRevokedAtIsNull(pet.getId(), effectiveScope).orElseGet(() -> {
            AppUser user = users.findById(userId).orElseThrow(() -> new NotFoundException("Usuário não encontrado"));
            Consent consent = consents.save(new Consent(pet, user, effectiveScope, Consent.CURRENT_VERSION,
                    Consent.DEFAULT_RECIPIENT, LocalDateTime.now(clock)));
            timeline.record(pet, "consent", "Compartilhamento com a clínica autorizado",
                    "Escopo: triagens e histórico recente. Versão do termo: " + consent.getVersion(), "tutor",
                    consent.getId(), null);
            return consent;
        });
    }

    @Transactional
    public void revoke(Long userId, Long consentId) {
        Consent consent = consents.findById(consentId)
                .filter(c -> c.getPet().getOwner().getId().equals(userId))
                .orElseThrow(() -> new NotFoundException("Consentimento não encontrado"));
        if (consent.isActive()) {
            consent.revoke(LocalDateTime.now(clock));
            triages.unshareAllOf(consent.getPet().getId());
            timeline.record(consent.getPet(), "consent", "Compartilhamento com a clínica revogado",
                    "A clínica deixa de ver as triagens deste pet.", "tutor", consent.getId(), null);
        }
    }

    @Transactional(readOnly = true)
    public List<ConsentResponse> list(Long userId, String petPublicId) {
        Pet pet = pets.requireOwned(userId, petPublicId);
        return consents.findByPetIdOrderByGrantedAtDesc(pet.getId()).stream().map(this::toResponse).toList();
    }

    /** Marca a triagem como compartilhada (o consentimento ativo é pré-requisito, garantido por {@link #grant}). */
    @Transactional
    public void shareTriage(Long triageId) {
        var triage = triages.markShared(triageId);
        timeline.record(triage.getPet(), "share", "Triagem enviada para a clínica",
                "Prioridade " + triage.getPriority().wire() + ". Aguardando revisão profissional.", "tutor",
                triage.getId(), null);
    }

    private ConsentResponse toResponse(Consent c) {
        return new ConsentResponse(c.getId(), c.getPet().getPublicId(), c.getScope(), c.getVersion(), c.getRecipient(),
                c.getGrantedAt().atOffset(ZoneOffset.UTC),
                c.getRevokedAt() == null ? null : c.getRevokedAt().atOffset(ZoneOffset.UTC), c.isActive());
    }
}
