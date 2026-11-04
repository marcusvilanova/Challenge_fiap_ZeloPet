package br.com.zelo.service;

import br.com.zelo.api.dto.ApiDtos.CareDoneRequest;
import br.com.zelo.api.dto.ApiDtos.CareItemRequest;
import br.com.zelo.api.dto.ApiDtos.CareItemResponse;
import br.com.zelo.chat.ConversationFlow.CareContext;
import br.com.zelo.domain.CareItem;
import br.com.zelo.domain.Pet;
import br.com.zelo.repo.CareItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

/** Plano de cuidados (Ciclo Zelo): o que está vencido ou chegando, para personalizar a conversa. */
@Service
public class CarePlanService {

    private static final int DUE_SOON_DAYS = 14;

    private static final DateTimeFormatter BR_DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", Locale.ROOT);

    private final CareItemRepository items;
    private final TimelineService timeline;
    private final Clock clock;

    public CarePlanService(CareItemRepository items, TimelineService timeline, Clock clock) {
        this.items = items;
        this.timeline = timeline;
        this.clock = clock;
    }

    /** Tutor adiciona um cuidado ao plano (vacina, vermífugo, antipulgas ou consulta). */
    @Transactional
    public CareItemResponse add(Pet pet, CareItemRequest request) {
        CareItem.Type type = parseType(request.type());
        LocalDate today = LocalDate.now(clock);
        if (request.lastDone() != null && request.nextDue().isBefore(request.lastDone())) {
            throw new BadRequestException("A próxima data não pode ser anterior à última aplicação.");
        }
        CareItem item = items.save(new CareItem(pet, type, request.name().trim(), request.lastDone(), request.nextDue()));
        timeline.record(pet, "note", "Cuidado adicionado ao plano: " + item.getName(),
                "Próxima data: " + BR_DATE.format(item.getNextDue()) + ".", "tutor", item.getId(), null);
        return toResponse(item, today);
    }

    /** Marca o cuidado como feito hoje e agenda o próximo (data informada ou intervalo padrão do tipo). */
    @Transactional
    public CareItemResponse markDone(Pet pet, Long itemId, CareDoneRequest request) {
        CareItem item = requireItem(pet, itemId);
        LocalDate today = LocalDate.now(clock);
        LocalDate next = request != null && request.nextDue() != null ? request.nextDue()
                : today.plusDays(item.getType().defaultIntervalDays());
        if (!next.isAfter(today)) {
            throw new BadRequestException("A próxima data precisa ser depois de hoje.");
        }
        item.markDone(today, next);
        items.save(item);
        timeline.record(pet, item.getType().timelineType(), "Cuidado realizado: " + item.getName(),
                "Registrado pelo tutor. Próxima data: " + BR_DATE.format(next) + ".", "tutor", item.getId(), null);
        return toResponse(item, today);
    }

    /** Remove o cuidado do plano (o histórico da linha do tempo é mantido). */
    @Transactional
    public void remove(Pet pet, Long itemId) {
        CareItem item = requireItem(pet, itemId);
        items.delete(item);
        timeline.record(pet, "note", "Cuidado removido do plano: " + item.getName(), null, "tutor", itemId, null);
    }

    private CareItem requireItem(Pet pet, Long itemId) {
        return items.findById(itemId).filter(i -> i.getPet().getId().equals(pet.getId()))
                .orElseThrow(() -> new NotFoundException("Cuidado não encontrado"));
    }

    private static CareItem.Type parseType(String value) {
        try {
            return CareItem.Type.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("Tipo de cuidado inválido. Use: "
                    + String.join(", ", Arrays.stream(CareItem.Type.values()).map(Enum::name).toList()));
        }
    }

    @Transactional(readOnly = true)
    public List<CareItemResponse> list(Pet pet) {
        LocalDate today = LocalDate.now(clock);
        return items.findByPetIdOrderByNextDueAsc(pet.getId()).stream().map(i -> toResponse(i, today)).toList();
    }

    @Transactional(readOnly = true)
    public CareContext contextFor(Pet pet) {
        LocalDate today = LocalDate.now(clock);
        List<CareItem> all = items.findByPetIdOrderByNextDueAsc(pet.getId());
        List<String> lines = all.stream().map(i -> describe(i, today)).toList();
        Optional<CareItem> mostUrgent = all.stream().filter(i -> daysUntil(i, today) <= DUE_SOON_DAYS).findFirst();
        String highlight = mostUrgent.map(i -> "Aproveitando: " + describeSentence(i, today)
                + " Vale agendar com a clínica.").orElse(null);
        return new CareContext(highlight, lines);
    }

    private CareItemResponse toResponse(CareItem item, LocalDate today) {
        long days = daysUntil(item, today);
        String status = days < 0 ? "OVERDUE" : days <= DUE_SOON_DAYS ? "DUE_SOON" : "OK";
        return new CareItemResponse(item.getId(), item.getType().name(), item.getName(), item.getLastDone(),
                item.getNextDue(), status, days);
    }

    private static long daysUntil(CareItem item, LocalDate today) {
        return ChronoUnit.DAYS.between(today, item.getNextDue());
    }

    private static String describe(CareItem item, LocalDate today) {
        long days = daysUntil(item, today);
        String when = days < 0 ? "venceu há " + plural(-days) : days == 0 ? "vence hoje" : "vence em " + plural(days);
        return item.getName() + ": " + when;
    }

    private static String describeSentence(CareItem item, LocalDate today) {
        long days = daysUntil(item, today);
        if (days < 0) {
            return "o item \"" + item.getName() + "\" venceu há " + plural(-days) + ".";
        }
        return days == 0 ? "o item \"" + item.getName() + "\" vence hoje."
                : "o item \"" + item.getName() + "\" vence em " + plural(days) + ".";
    }

    private static String plural(long days) {
        return days + (days == 1 ? " dia" : " dias");
    }
}
