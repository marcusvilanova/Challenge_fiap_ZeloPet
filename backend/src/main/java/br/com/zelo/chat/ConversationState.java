package br.com.zelo.chat;

import java.util.ArrayList;
import java.util.List;

/**
 * Estado persistido da conversa (JSON em chat_session.state_json). Imutável: cada passo devolve um novo estado.
 *
 * @param report       relatos do tutor acumulados na triagem em andamento (vira o texto de entrada da triagem)
 * @param pendingText  última queixa não reconhecida, guardada caso o tutor peça "enviar à clínica"
 * @param lastTriageId triagem mais recente da conversa (alvo do compartilhamento)
 */
public record ConversationState(
        Stage stage,
        List<StoredSymptom> symptoms,
        Integer durationHours,
        List<String> asked,
        int turns,
        String report,
        String pendingText,
        Long lastTriageId) {

    public enum Stage {
        IDLE,
        COLLECTING,
        AWAITING_SHARE,
        DONE
    }

    public record StoredSymptom(String id, String evidence) {
    }

    public ConversationState {
        symptoms = symptoms == null ? List.of() : List.copyOf(symptoms);
        asked = asked == null ? List.of() : List.copyOf(asked);
        stage = stage == null ? Stage.IDLE : stage;
    }

    public static ConversationState initial() {
        return new ConversationState(Stage.IDLE, List.of(), null, List.of(), 0, null, null, null);
    }

    /** Continua coletando: guarda sintomas, duração e relato acumulado; {@code askedSlot} registra a pergunta feita. */
    public ConversationState collecting(List<StoredSymptom> newSymptoms, Integer newDuration, String newReport,
                                        String askedSlot) {
        List<String> updated = new ArrayList<>(asked);
        if (askedSlot != null) {
            updated.add(askedSlot);
        }
        return new ConversationState(Stage.COLLECTING, newSymptoms, newDuration, updated, turns + 1, newReport,
                null, lastTriageId);
    }

    /** Fecha a triagem: zera a coleta e vai para {@code newStage}. */
    public ConversationState finished(Stage newStage) {
        return new ConversationState(newStage, List.of(), null, List.of(), turns + 1, null, null, lastTriageId);
    }

    public ConversationState withPendingText(String text) {
        return new ConversationState(stage, symptoms, durationHours, asked, turns + 1, report, text, lastTriageId);
    }

    public ConversationState withLastTriage(Long triageId) {
        return new ConversationState(stage, symptoms, durationHours, asked, turns, report, pendingText, triageId);
    }

    public ConversationState idle() {
        return new ConversationState(Stage.IDLE, symptoms, durationHours, asked, turns + 1, report, pendingText, lastTriageId);
    }

    public ConversationState doneKeepingTriage() {
        return new ConversationState(Stage.DONE, List.of(), null, List.of(), turns + 1, null, null, lastTriageId);
    }
}
