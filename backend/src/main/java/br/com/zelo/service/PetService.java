package br.com.zelo.service;

import br.com.zelo.ai.KnowledgeBase;
import br.com.zelo.ai.PetContext;
import br.com.zelo.ai.Species;
import br.com.zelo.api.dto.ApiDtos.CreatePetRequest;
import br.com.zelo.api.dto.ApiDtos.Option;
import br.com.zelo.api.dto.ApiDtos.PetCatalog;
import br.com.zelo.api.dto.ApiDtos.PetResponse;
import br.com.zelo.domain.AppUser;
import br.com.zelo.domain.Consent;
import br.com.zelo.domain.Pet;
import br.com.zelo.repo.AppUserRepository;
import br.com.zelo.repo.ConsentRepository;
import br.com.zelo.repo.PetRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Cadastro e acesso a pets. Todo acesso passa por {@link #requireOwned}: tutor nunca enxerga pet de outro. */
@Service
public class PetService {

    private final PetRepository pets;
    private final AppUserRepository users;
    private final ConsentRepository consents;
    private final KnowledgeBase knowledge;
    private final Clock clock;

    public PetService(PetRepository pets, AppUserRepository users, ConsentRepository consents,
                      KnowledgeBase knowledge, Clock clock) {
        this.pets = pets;
        this.users = users;
        this.consents = consents;
        this.knowledge = knowledge;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    public List<PetResponse> listMine(Long userId) {
        return pets.findByOwnerIdOrderByNameAsc(userId).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public Pet requireOwned(Long userId, String publicId) {
        long id = parse(publicId);
        return pets.findByIdAndOwnerId(id, userId).orElseThrow(() -> new NotFoundException("Pet não encontrado"));
    }

    @Transactional(readOnly = true)
    public PetResponse getMine(Long userId, String publicId) {
        return toResponse(requireOwned(userId, publicId));
    }

    @Transactional
    public PetResponse create(Long userId, CreatePetRequest request) {
        AppUser owner = users.findById(userId).orElseThrow(() -> new NotFoundException("Usuário não encontrado"));
        Species species = parseSpecies(request.species());
        Set<String> tags = validateTags(request.historyTags());
        Pet pet = pets.save(new Pet(owner, request.name().trim(), species, blankToNull(request.breed()),
                Boolean.TRUE.equals(request.female()), request.birthDate(), request.weightKg(), tags,
                LocalDateTime.now(clock)));
        return toResponse(pet);
    }

    /** Atualiza os dados do pet do tutor (404 se o pet for de outro tutor). */
    @Transactional
    public Pet update(Long userId, String publicId, CreatePetRequest request) {
        Pet pet = requireOwned(userId, publicId);
        pet.update(request.name().trim(), parseSpecies(request.species()), blankToNull(request.breed()),
                Boolean.TRUE.equals(request.female()), request.birthDate(), request.weightKg(),
                validateTags(request.historyTags()));
        return pets.save(pet);
    }

    public PetCatalog catalog() {
        return new PetCatalog(
                knowledge.breeds(Species.DOG).stream().map(b -> new Option(b.id(), b.label())).toList(),
                knowledge.breeds(Species.CAT).stream().map(b -> new Option(b.id(), b.label())).toList(),
                knowledge.historyTags().stream().map(t -> new Option(t.id(), t.label())).toList());
    }

    /** Dados mínimos enviados à IA: sem e-mail, sem nome do tutor. */
    public PetContext contextFor(Pet pet) {
        int months = pet.ageInMonths(LocalDate.now(clock));
        Double weight = pet.getWeightKg() == null ? null : pet.getWeightKg().doubleValue();
        return new PetContext(pet.getPublicId(), pet.getName(), pet.getSpecies(), months < 0 ? null : months,
                pet.getBreed(), knowledge.isBrachycephalic(pet.getSpecies(), pet.getBreed()), weight, pet.isFemale(),
                pet.historyTagSet());
    }

    public PetResponse toResponse(Pet pet) {
        int months = pet.ageInMonths(LocalDate.now(clock));
        boolean sharing = consents.findFirstByPetIdAndScopeAndRevokedAtIsNull(pet.getId(), Consent.SCOPE_TRIAGE_AND_CLINIC).isPresent();
        return new PetResponse(pet.getPublicId(), pet.getName(), pet.getSpecies().wire(), pet.getBreed(), pet.isFemale(),
                pet.getBirthDate(), months < 0 ? null : months, pet.getWeightKg(), List.copyOf(pet.historyTagSet()),
                knowledge.isBrachycephalic(pet.getSpecies(), pet.getBreed()), sharing);
    }

    private static long parse(String publicId) {
        try {
            return Pet.parsePublicId(publicId);
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("Pet não encontrado");
        }
    }

    private static Species parseSpecies(String value) {
        try {
            return Species.fromString(value);
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Espécie não suportada. Use cão ou gato.");
        }
    }

    private Set<String> validateTags(List<String> tags) {
        Set<String> known = new LinkedHashSet<>();
        knowledge.historyTags().forEach(t -> known.add(t.id()));
        Set<String> result = new LinkedHashSet<>();
        for (String tag : tags == null ? List.<String>of() : tags) {
            if (!known.contains(tag)) {
                throw new BadRequestException("Histórico de saúde desconhecido: " + tag);
            }
            result.add(tag);
        }
        return result;
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
