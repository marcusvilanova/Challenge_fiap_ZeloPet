package br.com.zelo.chat;

import br.com.zelo.ai.KnowledgeBase;
import br.com.zelo.ai.PetContext;
import br.com.zelo.ai.Priority;
import br.com.zelo.ai.SymptomMention;
import br.com.zelo.ai.TextNormalizer;
import br.com.zelo.ai.TriageAnalysis;
import br.com.zelo.ai.TriageEngine;
import br.com.zelo.chat.BotTurn.Kind;
import br.com.zelo.chat.BotTurn.ShareAction;
import br.com.zelo.chat.ConversationState.Stage;
import br.com.zelo.chat.ConversationState.StoredSymptom;

import java.util.List;
import java.util.Set;

/**
 * Máquina de estados da conversa. Determinística e sem E/S: recebe o estado e a mensagem, devolve o próximo
 * turno. Regras: sinal de alerta encerra as perguntas na hora; no máximo duas perguntas de acompanhamento;
 * nada é compartilhado com a clínica sem um "sim" explícito do tutor.
 */
public final class ConversationFlow {

    private static final int MAX_FOLLOW_UPS = 2;
    private static final int MIN_WORDS_FOR_PENDING = 4;
    private static final double LOW_CONFIDENCE = 0.60;
    private static final Set<String> INTAKE_RELEVANT = Set.of(
            "vomito", "vomito_repetido", "diarreia", "apatia", "febre", "fraqueza", "nausea", "dor_abdominal");

    static final String SLOT_DURATION = "duration";
    static final String SLOT_INTAKE = "intake";
    static final String SLOT_BREATHING = "breathing";

    /** Cuidados do pet usados para personalizar a conversa. */
    public record CareContext(String highlight, List<String> lines) {
    }

    /** Ponto de extensão para o LLM sugerir sintomas que o léxico não reconheceu (opcional). */
    @FunctionalInterface
    public interface SymptomAssist {
        List<SymptomMention> extra(String text, TriageAnalysis current);
    }

    private final TriageEngine engine;
    private final KnowledgeBase knowledge;
    private final IntentClassifier intents = new IntentClassifier();
    private final ReplyComposer composer = new ReplyComposer();

    public ConversationFlow(TriageEngine engine, KnowledgeBase knowledge) {
        this.engine = engine;
        this.knowledge = knowledge;
    }

    public BotTurn next(ConversationState state, String userText, PetContext pet, CareContext care, SymptomAssist assist) {
        String normalized = TextNormalizer.normalize(userText);
        Intent intent = intents.classify(normalized);

        if (state.stage() == Stage.AWAITING_SHARE && (intent == Intent.YES || intent == Intent.SHARE)) {
            return shareTurn(state, pet, ShareAction.GRANT);
        }
        if (state.stage() == Stage.AWAITING_SHARE && intent == Intent.NO) {
            return shareTurn(state, pet, ShareAction.DECLINE);
        }

        // saudações, agradecimentos, atalhos e "sim/não" nunca saem para o LLM (nem enviam o texto a terceiros)
        boolean clinicalCandidate = intent == Intent.OTHER || intent == Intent.MEDICATION_REQUEST;
        TriageAnalysis analysis = analyze(userText, pet, state, clinicalCandidate ? assist : null);
        boolean symptomFlow = analysis.hasSymptoms() || !analysis.negated().isEmpty();
        if (symptomFlow) {
            return symptomTurn(state, userText, pet, care, analysis, intent);
        }
        return nonClinicalTurn(state, userText, normalized, pet, care, intent);
    }

    /** Análise avulsa de um relato (endpoint /api/triage), sem estado de conversa. */
    public TriageAnalysis analyzeStateless(String text, PetContext pet, SymptomAssist assist) {
        return analyze(text, pet, ConversationState.initial(), assist);
    }

    // ----------------------------------------------------------------- fluxo clínico
    private TriageAnalysis analyze(String text, PetContext pet, ConversationState state, SymptomAssist assist) {
        List<SymptomMention> prior = rehydrate(state, pet);
        TriageAnalysis analysis = engine.analyze(text, pet, prior, state.durationHours());
        boolean weak = analysis.unrecognized() || analysis.vague() || analysis.confidence() < LOW_CONFIDENCE;
        if (assist != null && weak) {
            List<SymptomMention> extra = assist.extra(text, analysis);
            if (!extra.isEmpty()) {
                // o LLM só soma: a prioridade da análise determinística original é o piso
                return engine.analyze(text, pet, prior, state.durationHours(), extra).withFloor(analysis);
            }
        }
        return analysis;
    }

    private BotTurn symptomTurn(ConversationState state, String text, PetContext pet, CareContext care,
                                TriageAnalysis analysis, Intent intent) {
        String report = state.report() == null ? text : state.report() + "\n" + text;
        boolean stop = analysis.emergency() || intent == Intent.FINISH || !analysis.hasSymptoms();
        String slot = stop ? null : nextSlot(analysis, state);
        if (slot != null) {
            return questionTurn(state, pet, analysis, slot, report);
        }
        return triageTurn(state, pet, care, analysis, report, intent == Intent.MEDICATION_REQUEST);
    }

    private String nextSlot(TriageAnalysis analysis, ConversationState state) {
        boolean canAsk = state.asked().size() < MAX_FOLLOW_UPS && analysis.priority().level() < Priority.ALTA.level();
        if (!canAsk) {
            return null;
        }
        Set<String> ids = ids(analysis);
        if (analysis.durationHours() == null && !state.asked().contains(SLOT_DURATION)) {
            return SLOT_DURATION;
        }
        boolean intakeRelevant = ids.stream().anyMatch(INTAKE_RELEVANT::contains);
        if (intakeRelevant && !ids.contains("perda_apetite") && !ids.contains("recusa_agua")
                && !state.asked().contains(SLOT_INTAKE)) {
            return SLOT_INTAKE;
        }
        boolean respiratory = analysis.active().stream().anyMatch(m -> "respiratorio".equals(m.category()));
        if (respiratory && !ids.contains("dificuldade_respirar") && !state.asked().contains(SLOT_BREATHING)) {
            return SLOT_BREATHING;
        }
        return null;
    }

    private BotTurn questionTurn(ConversationState state, PetContext pet, TriageAnalysis analysis, String slot,
                                 String report) {
        ConversationState next = state.collecting(stored(analysis), analysis.durationHours(), report, slot);
        return switch (slot) {
            case SLOT_DURATION -> BotTurn.simple(Kind.QUESTION, composer.askDuration(pet, analysis),
                    List.of("Desde hoje", "Há 1 ou 2 dias", "Há mais de 3 dias"), next);
            case SLOT_INTAKE -> BotTurn.simple(Kind.QUESTION, composer.askIntake(pet),
                    List.of("Está comendo e bebendo normal", "Não está comendo", "Não está bebendo água"), next);
            default -> BotTurn.simple(Kind.QUESTION, composer.askBreathing(pet),
                    List.of("Respira normalmente", "Respira com dificuldade"), next);
        };
    }

    private BotTurn triageTurn(ConversationState state, PetContext pet, CareContext care, TriageAnalysis analysis,
                               String report, boolean medicationAsked) {
        boolean askToShare = analysis.priority().level() >= Priority.MEDIA.level();
        String text = composer.triage(analysis, pet, care.highlight(), askToShare);
        if (medicationAsked) {
            text = composer.medicationRefusal() + "\n\n" + text;
        }
        List<String> quick = askToShare
                ? List.of("Sim, compartilhar com a clínica", "Agora não")
                : List.of("Relatar outro sintoma", "Próximos cuidados");
        ConversationState next = state.finished(askToShare ? Stage.AWAITING_SHARE : Stage.DONE);
        return new BotTurn(Kind.TRIAGE, text, quick, analysis, report, ShareAction.NONE, next, true);
    }

    // -------------------------------------------------------------- fluxo não clínico
    private BotTurn nonClinicalTurn(ConversationState state, String text, String normalized, PetContext pet,
                                    CareContext care, Intent intent) {
        ConversationState idle = state.idle();
        return switch (intent) {
            case GREETING -> BotTurn.simple(Kind.SMALLTALK, composer.greeting(pet, care.highlight()), menu(), idle);
            case THANKS -> BotTurn.simple(Kind.SMALLTALK, composer.thanks(pet), List.of("Relatar um sintoma"), idle);
            case FAREWELL -> BotTurn.simple(Kind.SMALLTALK, composer.farewell(pet), List.of(), idle);
            case HELP -> BotTurn.simple(Kind.SMALLTALK, composer.help(pet), menu(), idle);
            case CARE_PLAN -> BotTurn.simple(Kind.CARE_PLAN, composer.carePlan(pet, care.lines()),
                    List.of("Relatar um sintoma"), idle);
            case NEW_REPORT -> BotTurn.simple(Kind.SMALLTALK, composer.askForReport(pet), List.of(), idle);
            case MEDICATION_REQUEST -> BotTurn.simple(Kind.SMALLTALK,
                    composer.medicationRefusal() + "\n\nSe " + pet.pronoun() + " estiver com algum sinal, me conte e eu oriento o próximo passo.",
                    List.of("Relatar um sintoma"), idle);
            case SHARE -> shareWithoutQuestion(state, pet, idle);
            default -> clarifyTurn(state, text, normalized, pet);
        };
    }

    private BotTurn shareWithoutQuestion(ConversationState state, PetContext pet, ConversationState idle) {
        // o relato ainda não triado (pendente) vem antes: é ele que o tutor acabou de pedir para a clínica avaliar
        if (state.pendingText() != null) {
            TriageAnalysis pending = engine.analyze(state.pendingText(), pet, List.of(), null);
            String reply = composer.triage(pending, pet, null, false) + "\n\n" + composer.shareGranted(pet);
            return new BotTurn(Kind.TRIAGE, reply, List.of("Relatar outro sintoma", "Próximos cuidados"), pending,
                    state.pendingText(), ShareAction.GRANT, idle.finished(Stage.DONE), true);
        }
        if (state.lastTriageId() != null) {
            return shareTurn(state, pet, ShareAction.GRANT);
        }
        return BotTurn.simple(Kind.SHARE, composer.noTriageToShare(pet), List.of("Relatar um sintoma"), idle);
    }

    private BotTurn clarifyTurn(ConversationState state, String text, String normalized, PetContext pet) {
        boolean worthKeeping = normalized.split(" ").length >= MIN_WORDS_FOR_PENDING;
        ConversationState next = worthKeeping ? state.idle().withPendingText(text) : state.idle();
        List<String> quick = worthKeeping
                ? List.of("Descrever sintomas", "Enviar para a clínica avaliar", "Próximos cuidados")
                : List.of("Descrever sintomas", "Próximos cuidados", "O que você faz?");
        return BotTurn.simple(Kind.CLARIFY, composer.clarify(pet), quick, next);
    }

    private BotTurn shareTurn(ConversationState state, PetContext pet, ShareAction action) {
        String reply = action == ShareAction.GRANT ? composer.shareGranted(pet) : composer.shareDeclined();
        return new BotTurn(Kind.SHARE, reply, List.of("Relatar outro sintoma", "Próximos cuidados"), null, null, action,
                state.doneKeepingTriage(), false);
    }

    // ------------------------------------------------------------------- utilidades
    private static List<String> menu() {
        return List.of("Relatar um sintoma", "Próximos cuidados", "O que você faz?");
    }

    private static Set<String> ids(TriageAnalysis analysis) {
        return analysis.active().stream().map(SymptomMention::id).collect(java.util.stream.Collectors.toSet());
    }

    private static List<StoredSymptom> stored(TriageAnalysis analysis) {
        return analysis.active().stream().map(m -> new StoredSymptom(m.id(), m.evidence())).toList();
    }

    private List<SymptomMention> rehydrate(ConversationState state, PetContext pet) {
        return state.symptoms().stream().filter(s -> knowledge.knows(s.id())).map(s -> {
            var def = knowledge.symptom(s.id());
            return new SymptomMention(def.id(), def.label(), def.category(), def.severityFor(pet.species()),
                    s.evidence(), false, 0, 0);
        }).toList();
    }
}
