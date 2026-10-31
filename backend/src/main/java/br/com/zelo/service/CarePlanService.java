package br.com.zelo.service;

import br.com.zelo.api.dto.ApiDtos.CareItemResponse;
import br.com.zelo.chat.ConversationFlow.CareContext;
import br.com.zelo.domain.CareItem;
import br.com.zelo.domain.Pet;
import br.com.zelo.repo.CareItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;

/** Plano de cuidados (Ciclo Zelo): o que está vencido ou chegando, para personalizar a conversa. */
@Service
public class CarePlanService {

    private static final int DUE_SOON_DAYS = 14;

    private final CareItemRepository items;
    private final Clock clock;

    public CarePlanService(CareItemRepository items, Clock clock) {
        this.items = items;
        this.clock = clock;
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
