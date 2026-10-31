package br.com.zelo.chat;

import br.com.zelo.ai.TriageAnalysis;

import java.util.List;

/**
 * Resultado de um passo da conversa.
 *
 * @param triage análise a ser persistida como triagem; {@code null} quando o turno não fecha uma triagem
 * @param share  o que fazer com o consentimento de compartilhamento neste turno
 * @param draft  {@code true} quando o texto é um rascunho seguro que o LLM pode reescrever (só triagens)
 */
public record BotTurn(
        Kind kind,
        String text,
        List<String> quickReplies,
        TriageAnalysis triage,
        String reportText,
        ShareAction share,
        ConversationState nextState,
        boolean draft) {

    public enum Kind {
        SMALLTALK,
        QUESTION,
        TRIAGE,
        SHARE,
        CLARIFY,
        CARE_PLAN
    }

    public enum ShareAction {
        NONE,
        GRANT,
        DECLINE
    }

    public BotTurn {
        quickReplies = quickReplies == null ? List.of() : List.copyOf(quickReplies);
    }

    public static BotTurn simple(Kind kind, String text, List<String> quickReplies, ConversationState next) {
        return new BotTurn(kind, text, quickReplies, null, null, ShareAction.NONE, next, false);
    }
}
