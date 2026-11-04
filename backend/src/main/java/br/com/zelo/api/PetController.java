package br.com.zelo.api;

import br.com.zelo.api.dto.ApiDtos.CareDoneRequest;
import br.com.zelo.api.dto.ApiDtos.CareItemRequest;
import br.com.zelo.api.dto.ApiDtos.CareItemResponse;
import br.com.zelo.api.dto.ApiDtos.CreatePetRequest;
import br.com.zelo.api.dto.ApiDtos.PetCatalog;
import br.com.zelo.api.dto.ApiDtos.PetResponse;
import br.com.zelo.api.dto.ApiDtos.TimelineEventResponse;
import br.com.zelo.domain.Pet;
import br.com.zelo.security.AppUserDetails;
import br.com.zelo.service.BadRequestException;
import br.com.zelo.service.CarePlanService;
import br.com.zelo.service.PetService;
import br.com.zelo.service.TimelineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/pets")
@Tag(name = "Pets")
public class PetController {

    private static final int MAX_TIMELINE = 100;
    private static final Set<String> MANUAL_EVENT_TYPES = Set.of("note", "vaccine", "medication", "visit", "behavior");

    private final PetService pets;
    private final TimelineService timeline;
    private final CarePlanService carePlan;

    public PetController(PetService pets, TimelineService timeline, CarePlanService carePlan) {
        this.pets = pets;
        this.timeline = timeline;
        this.carePlan = carePlan;
    }

    public record EventRequest(@NotBlank String eventType, @NotBlank @Size(max = 160) String title,
                               @Size(max = 480) String text) {
    }

    @Operation(summary = "Pets do tutor autenticado")
    @GetMapping
    public List<PetResponse> list(@AuthenticationPrincipal AppUserDetails me) {
        return pets.listMine(me.id());
    }

    @Operation(summary = "Raças (dataset D2) e opções de histórico de saúde para o cadastro")
    @GetMapping("/catalog")
    public PetCatalog catalog() {
        return pets.catalog();
    }

    @Operation(summary = "Cadastra um pet")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PetResponse create(@AuthenticationPrincipal AppUserDetails me, @Valid @RequestBody CreatePetRequest request) {
        return pets.create(me.id(), request);
    }

    @Operation(summary = "Consulta um pet do tutor (404 se for de outro tutor)")
    @GetMapping("/{petId}")
    public PetResponse get(@AuthenticationPrincipal AppUserDetails me, @PathVariable String petId) {
        return pets.getMine(me.id(), petId);
    }

    @Operation(summary = "Atualiza os dados do pet (404 se for de outro tutor)")
    @PutMapping("/{petId}")
    public PetResponse update(@AuthenticationPrincipal AppUserDetails me, @PathVariable String petId,
                              @Valid @RequestBody CreatePetRequest request) {
        Pet pet = pets.update(me.id(), petId, request);
        timeline.record(pet, "note", "Dados do pet atualizados", "Ficha editada pelo tutor no painel.", "tutor", null, null);
        return pets.toResponse(pet);
    }

    @Operation(summary = "Linha do tempo longitudinal do pet")
    @GetMapping("/{petId}/timeline")
    public List<TimelineEventResponse> timeline(@AuthenticationPrincipal AppUserDetails me, @PathVariable String petId,
                                                @RequestParam(defaultValue = "30") int limit) {
        Pet pet = pets.requireOwned(me.id(), petId);
        return timeline.latest(pet.getId(), Math.max(1, Math.min(limit, MAX_TIMELINE)));
    }

    @Operation(summary = "Plano de cuidados (Ciclo Zelo): vacinas, vermífugo, retornos")
    @GetMapping("/{petId}/care-plan")
    public List<CareItemResponse> carePlan(@AuthenticationPrincipal AppUserDetails me, @PathVariable String petId) {
        return carePlan.list(pets.requireOwned(me.id(), petId));
    }

    @Operation(summary = "Adiciona um cuidado ao plano (VACINA, VERMIFUGO, ANTIPULGAS ou CONSULTA)")
    @PostMapping("/{petId}/care-plan")
    @ResponseStatus(HttpStatus.CREATED)
    public CareItemResponse addCare(@AuthenticationPrincipal AppUserDetails me, @PathVariable String petId,
                                    @Valid @RequestBody CareItemRequest request) {
        return carePlan.add(pets.requireOwned(me.id(), petId), request);
    }

    @Operation(summary = "Marca um cuidado como feito hoje e agenda o próximo")
    @PostMapping("/{petId}/care-plan/{itemId}/done")
    public CareItemResponse markCareDone(@AuthenticationPrincipal AppUserDetails me, @PathVariable String petId,
                                         @PathVariable Long itemId, @RequestBody(required = false) CareDoneRequest request) {
        return carePlan.markDone(pets.requireOwned(me.id(), petId), itemId, request);
    }

    @Operation(summary = "Remove um cuidado do plano")
    @DeleteMapping("/{petId}/care-plan/{itemId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void removeCare(@AuthenticationPrincipal AppUserDetails me, @PathVariable String petId,
                           @PathVariable Long itemId) {
        carePlan.remove(pets.requireOwned(me.id(), petId), itemId);
    }

    @Operation(summary = "Registra um evento manual na linha do tempo")
    @PostMapping("/{petId}/events")
    @ResponseStatus(HttpStatus.CREATED)
    public void addEvent(@AuthenticationPrincipal AppUserDetails me, @PathVariable String petId,
                         @Valid @RequestBody EventRequest request) {
        if (!MANUAL_EVENT_TYPES.contains(request.eventType())) {
            throw new BadRequestException("Tipo de evento inválido. Use: " + String.join(", ", MANUAL_EVENT_TYPES));
        }
        Pet pet = pets.requireOwned(me.id(), petId);
        timeline.record(pet, request.eventType(), request.title().trim(), request.text(), "tutor", null, null);
    }
}
