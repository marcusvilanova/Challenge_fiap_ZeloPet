package br.com.zelo.config;

import br.com.zelo.ai.Priority;
import br.com.zelo.ai.Species;
import br.com.zelo.api.dto.ApiDtos.ChatReply;
import br.com.zelo.api.dto.ApiDtos.ChatRequest;
import br.com.zelo.api.dto.ApiDtos.ReviewRequest;
import br.com.zelo.domain.AppUser;
import br.com.zelo.domain.ClinicReview.Decision;
import br.com.zelo.domain.Pet;
import br.com.zelo.domain.Role;
import br.com.zelo.domain.Triage;
import br.com.zelo.repo.AppUserRepository;
import br.com.zelo.repo.PetRepository;
import br.com.zelo.repo.TriageRepository;
import br.com.zelo.security.AppUserDetails;
import br.com.zelo.service.ChatService;
import br.com.zelo.service.ClinicService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.EntityManager;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Casos FICTÍCIOS para o painel da clínica (demo/clinic-scenarios.json, o mesmo arquivo da versão online).
 * Cada conversa passa pelo fluxo real (ChatService → motor de triagem → consentimento → compartilhamento) e, quando o
 * caso pede, recebe a revisão da clínica. Depois as datas são recuadas para a fila e o painel mostrarem uma semana.
 */
@Component
public class ClinicDemoScenarios {

    static final String RESOURCE = "demo/clinic-scenarios.json";

    private final AppUserRepository users;
    private final PetRepository pets;
    private final TriageRepository triages;
    private final ChatService chat;
    private final ClinicService clinic;
    private final EntityManager entityManager;
    private final ObjectMapper mapper;
    private final Clock clock;

    public ClinicDemoScenarios(AppUserRepository users, PetRepository pets, TriageRepository triages, ChatService chat,
                               ClinicService clinic, EntityManager entityManager, ObjectMapper mapper, Clock clock) {
        this.users = users;
        this.pets = pets;
        this.triages = triages;
        this.chat = chat;
        this.clinic = clinic;
        this.entityManager = entityManager;
        this.mapper = mapper;
        this.clock = clock;
    }

    /** Roda dentro da transação do {@link DemoDataSeeder}. */
    public void seed(String passwordHash, AppUser vet) {
        JsonNode root = read();
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = LocalDate.now(clock);
        for (JsonNode tutorNode : root.path("tutors")) {
            AppUser tutor = users.save(new AppUser(tutorNode.get("email").asText(), tutorNode.get("name").asText(),
                    passwordHash, Role.TUTOR, now.minusDays(30)));
            AppUserDetails tutorDetails = AppUserDetails.from(tutor);
            for (JsonNode petNode : tutorNode.path("pets")) {
                Pet pet = pets.save(new Pet(tutor, petNode.get("name").asText(),
                        Species.fromString(petNode.get("species").asText()), petNode.get("breed").asText(),
                        petNode.get("female").asBoolean(), today.minusMonths(petNode.get("age_months").asInt()),
                        new BigDecimal(petNode.get("weight_kg").asText()), tags(petNode), now.minusDays(30)));
                String sharedTriageId = converse(tutorDetails, pet, petNode);
                JsonNode review = petNode.get("review");
                if (sharedTriageId != null && review != null) {
                    review(vet, sharedTriageId, review);
                }
                LocalDateTime when = now.minusMinutes(Math.round(petNode.get("hours_ago").asDouble() * 60));
                LocalDateTime reviewedAt = review == null ? when : when.plusMinutes(review.get("after_minutes").asLong());
                backdate(pet, when, reviewedAt);
            }
        }
    }

    private String converse(AppUserDetails tutor, Pet pet, JsonNode petNode) {
        Long sessionId = null;
        String shared = null;
        for (JsonNode message : petNode.path("messages")) {
            ChatReply reply = chat.handle(tutor, new ChatRequest(pet.getPublicId(), sessionId, message.asText()));
            sessionId = reply.sessionId();
            if (reply.sharedTriageId() != null) {
                shared = reply.sharedTriageId();
            }
        }
        return shared;
    }

    private void review(AppUser vet, String triageId, JsonNode review) {
        Decision decision = Decision.valueOf(review.get("decision").asText());
        Priority suggested = triages.findById(Triage.parsePublicId(triageId))
                .orElseThrow().getPriority();
        Priority finalPriority = decision == Decision.CONFIRMADA ? suggested
                : Priority.fromWire(review.get("final_priority").asText());
        if (decision == Decision.AJUSTADA && finalPriority == suggested) {
            decision = Decision.CONFIRMADA; // o motor já sugeriu o mesmo nível: vira confirmação
        }
        clinic.review(vet.getId(), new ReviewRequest(triageId, decision, finalPriority, review.path("note").asText(null)));
    }

    /** Recuo das datas do caso: conversa, triagem, consentimento e envio em {@code when}; revisão em {@code reviewedAt}. */
    private void backdate(Pet pet, LocalDateTime when, LocalDateTime reviewedAt) {
        entityManager.flush();
        update("update ChatSession s set s.createdAt = :ts, s.updatedAt = :ts where s.pet = :pet", pet, when);
        update("update ChatMessage m set m.createdAt = :ts where m.session.id in "
                + "(select s.id from ChatSession s where s.pet = :pet)", pet, when);
        update("update Triage t set t.createdAt = :ts where t.pet = :pet", pet, when);
        update("update Consent c set c.grantedAt = :ts where c.pet = :pet", pet, when);
        update("update TimelineEvent e set e.occurredAt = :ts where e.pet = :pet and e.type <> 'review'", pet, when);
        update("update TimelineEvent e set e.occurredAt = :ts where e.pet = :pet and e.type = 'review'", pet, reviewedAt);
        update("update ClinicReview r set r.createdAt = :ts where r.triage.id in "
                + "(select t.id from Triage t where t.pet = :pet)", pet, reviewedAt);
        entityManager.clear();
    }

    private void update(String jpql, Pet pet, LocalDateTime timestamp) {
        entityManager.createQuery(jpql).setParameter("ts", timestamp).setParameter("pet", pet).executeUpdate();
    }

    private static Set<String> tags(JsonNode petNode) {
        Set<String> tags = new LinkedHashSet<>();
        petNode.path("history_tags").forEach(t -> tags.add(t.asText()));
        return tags;
    }

    private JsonNode read() {
        try (InputStream in = new ClassPathResource(RESOURCE).getInputStream()) {
            return mapper.readTree(in);
        } catch (IOException e) {
            throw new UncheckedIOException("Não foi possível ler " + RESOURCE, e);
        }
    }
}
