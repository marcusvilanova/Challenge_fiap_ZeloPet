package br.com.zelo.service;

import br.com.zelo.api.dto.ApiDtos.TimelineEventResponse;
import br.com.zelo.domain.Pet;
import br.com.zelo.domain.TimelineEvent;
import br.com.zelo.repo.TimelineEventRepository;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

/** Linha do tempo longitudinal do pet. Todo evento registra a origem (source) da informação. */
@Service
public class TimelineService {

    private static final int MAX_SUMMARY = 480;

    private final TimelineEventRepository events;
    private final Clock clock;

    public TimelineService(TimelineEventRepository events, Clock clock) {
        this.events = events;
        this.clock = clock;
    }

    @Transactional
    public void record(Pet pet, String type, String title, String summary, String source, Long refId, String payloadJson) {
        String safeSummary = summary != null && summary.length() > MAX_SUMMARY ? summary.substring(0, MAX_SUMMARY) : summary;
        events.save(new TimelineEvent(pet, type, title, safeSummary, source, refId, payloadJson, LocalDateTime.now(clock)));
    }

    /** Tipos que a clínica pode ver; anotações livres e de comportamento do tutor ficam de fora (minimização). */
    private static final List<String> CLINIC_VISIBLE_TYPES =
            List.of("triage", "review", "consent", "share", "vision", "visit", "vaccine", "medication");

    @Transactional(readOnly = true)
    public List<TimelineEventResponse> latest(Long petId, int limit) {
        return toResponses(events.findByPetIdOrderByOccurredAtDescIdDesc(petId, PageRequest.of(0, limit)));
    }

    @Transactional(readOnly = true)
    public List<TimelineEventResponse> latestForClinic(Long petId, int limit) {
        return toResponses(events.findByPetIdAndTypeInOrderByOccurredAtDescIdDesc(petId, CLINIC_VISIBLE_TYPES,
                PageRequest.of(0, limit)));
    }

    private static List<TimelineEventResponse> toResponses(List<TimelineEvent> found) {
        return found.stream()
                .map(e -> new TimelineEventResponse(e.getId(), e.getType(), e.getTitle(), e.getSummary(), e.getSource(),
                        e.getRefId() == null ? null : String.valueOf(e.getRefId()),
                        e.getOccurredAt().atOffset(ZoneOffset.UTC)))
                .toList();
    }
}
