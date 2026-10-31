package br.com.zelo.chat;

import br.com.zelo.ai.PetContext;
import br.com.zelo.ai.Priority;
import br.com.zelo.ai.SymptomMention;
import br.com.zelo.ai.TestEngines;
import br.com.zelo.chat.BotTurn.Kind;
import br.com.zelo.chat.BotTurn.ShareAction;
import br.com.zelo.chat.ConversationFlow.CareContext;
import br.com.zelo.chat.ConversationState.Stage;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ConversationFlowTest {

    private static final CareContext NO_CARE = new CareContext(null, List.of());

    private ConversationFlow flow;
    private PetContext mel;

    @BeforeEach
    void setUp() {
        flow = new ConversationFlow(TestEngines.ENGINE, TestEngines.KNOWLEDGE);
        mel = TestEngines.dog();
    }

    private BotTurn say(ConversationState state, String text) {
        return flow.next(state, text, mel, NO_CARE, null);
    }

    @Test
    @DisplayName("saudação responde com menu e personaliza com o nome do pet")
    void greeting_isPersonalizedWithMenu() {
        BotTurn turn = say(ConversationState.initial(), "Oi, tudo bem?");

        assertThat(turn.kind()).isEqualTo(Kind.SMALLTALK);
        assertThat(turn.text()).contains("da Mel");
        assertThat(turn.quickReplies()).contains("Relatar um sintoma");
    }

    @Test
    @DisplayName("sintoma leve: pergunta primeiro a duração, depois fecha a triagem com a resposta")
    void mildSymptom_asksDurationThenTriages() {
        BotTurn first = say(ConversationState.initial(), "ela está com tosse");

        assertThat(first.kind()).isEqualTo(Kind.QUESTION);
        assertThat(first.text()).contains("Desde quando");
        assertThat(first.quickReplies()).contains("Desde hoje");
        assertThat(first.nextState().stage()).isEqualTo(Stage.COLLECTING);

        BotTurn second = say(first.nextState(), "Há 1 ou 2 dias");
        // tosse é respiratório: ainda pode perguntar sobre a respiração antes de fechar
        BotTurn last = second.kind() == Kind.QUESTION ? say(second.nextState(), "Respira normalmente") : second;

        assertThat(last.kind()).isEqualTo(Kind.TRIAGE);
        assertThat(last.triage().priority()).isEqualTo(Priority.MEDIA);
        assertThat(last.triage().durationHours()).isEqualTo(48);
        assertThat(last.reportText()).contains("tosse");
        assertThat(last.nextState().stage()).isEqualTo(Stage.AWAITING_SHARE);
        assertThat(last.quickReplies()).contains("Sim, compartilhar com a clínica");
    }

    @Test
    @DisplayName("sinal de alerta encerra as perguntas na hora e oferece avisar a clínica")
    void emergency_skipsQuestions() {
        BotTurn turn = say(ConversationState.initial(), "ela teve uma convulsão");

        assertThat(turn.kind()).isEqualTo(Kind.TRIAGE);
        assertThat(turn.triage().priority()).isEqualTo(Priority.EMERGENCIA);
        assertThat(turn.text()).contains("emergência").contains("enquanto você se desloca");
        assertThat(turn.text()).doesNotContainIgnoringCase("dipirona");
    }

    @Test
    @DisplayName("prioridade alta também não faz perguntas de acompanhamento")
    void highPriority_skipsQuestions() {
        BotTurn turn = say(ConversationState.initial(), "ela está abatida e vomitou duas vezes");

        assertThat(turn.kind()).isEqualTo(Kind.TRIAGE);
        assertThat(turn.triage().priority()).isEqualTo(Priority.ALTA);
    }

    @Test
    @DisplayName("no máximo duas perguntas de acompanhamento")
    void followUps_areCapped() {
        BotTurn q1 = say(ConversationState.initial(), "está vomitando");
        BotTurn q2 = say(q1.nextState(), "Não sei");
        BotTurn result = say(q2.nextState(), "Não sei");

        assertThat(q1.kind()).isEqualTo(Kind.QUESTION);
        assertThat(q2.kind()).isEqualTo(Kind.QUESTION);
        assertThat(result.kind()).isEqualTo(Kind.TRIAGE);
    }

    @Test
    @DisplayName("'sim' depois da triagem concede o compartilhamento; 'agora não' recusa")
    void shareQuestion_yesAndNo() {
        ConversationState awaiting = say(ConversationState.initial(), "ela teve uma convulsão").nextState();

        BotTurn yes = say(awaiting, "Sim, compartilhar com a clínica");
        BotTurn no = say(awaiting, "Agora não");

        assertThat(yes.share()).isEqualTo(ShareAction.GRANT);
        assertThat(no.share()).isEqualTo(ShareAction.DECLINE);
        assertThat(no.text()).contains("não compartilhei nada");
    }

    @Test
    @DisplayName("nada é compartilhado se o tutor só responde outra coisa")
    void shareQuestion_unrelatedAnswerDoesNotShare() {
        ConversationState awaiting = say(ConversationState.initial(), "ela teve uma convulsão").nextState();

        BotTurn turn = say(awaiting, "obrigada");

        assertThat(turn.share()).isEqualTo(ShareAction.NONE);
    }

    @Test
    @DisplayName("pedido de remédio sem sintomas: recusa com explicação de segurança")
    void medicationRequest_isRefused() {
        BotTurn turn = say(ConversationState.initial(), "posso dar dipirona pra ela?");

        assertThat(turn.text()).contains("Não posso indicar remédios");
        assertThat(turn.triage()).isNull();
    }

    @Test
    @DisplayName("pedido de remédio junto de sintomas: recusa e ainda faz a triagem")
    void medicationRequest_withSymptoms_stillTriages() {
        BotTurn turn = say(ConversationState.initial(), "ela teve uma convulsão, posso dar dipirona?");

        assertThat(turn.text()).contains("Não posso indicar remédios");
        assertThat(turn.triage().priority()).isEqualTo(Priority.EMERGENCIA);
    }

    @Test
    @DisplayName("texto não reconhecido: pede esclarecimento com aviso de segurança e guarda para envio à clínica")
    void unknownText_clarifiesWithSafetyBanner() {
        BotTurn turn = say(ConversationState.initial(), "ela está com um negócio no zóio direito");

        assertThat(turn.kind()).isEqualTo(Kind.CLARIFY);
        assertThat(turn.text()).contains("procure um pronto-atendimento");
        assertThat(turn.quickReplies()).contains("Enviar para a clínica avaliar");
        assertThat(turn.nextState().pendingText()).contains("negócio no zóio");
    }

    @Test
    @DisplayName("'enviar para a clínica' após texto não reconhecido gera triagem média com revisão humana e compartilha")
    void sendUnrecognizedToClinic_triagesAndShares() {
        ConversationState pending = say(ConversationState.initial(), "ela está com um negócio no zóio direito").nextState();

        BotTurn turn = say(pending, "Enviar para a clínica avaliar");

        assertThat(turn.share()).isEqualTo(ShareAction.GRANT);
        assertThat(turn.triage().priority()).isEqualTo(Priority.MEDIA);
        assertThat(turn.triage().humanReviewRequired()).isTrue();
    }

    @Test
    @DisplayName("compartilhar sem nenhuma triagem anterior explica que não há nada para enviar")
    void share_withoutTriage_explains() {
        BotTurn turn = say(ConversationState.initial(), "quero compartilhar com a clínica");

        assertThat(turn.share()).isEqualTo(ShareAction.NONE);
        assertThat(turn.text()).contains("Ainda não tenho uma triagem");
    }

    @Test
    @DisplayName("próximos cuidados lista o plano do pet")
    void carePlan_listsItems() {
        CareContext care = new CareContext("A V10 venceu.", List.of("V10: venceu há 12 dias", "Vermífugo: em 20 dias"));

        BotTurn turn = flow.next(ConversationState.initial(), "quais as próximas vacinas?", mel, care, null);

        assertThat(turn.kind()).isEqualTo(Kind.CARE_PLAN);
        assertThat(turn.text()).contains("• V10: venceu há 12 dias").contains("• Vermífugo");
    }

    @Test
    @DisplayName("nome do pet com '%' não quebra a formatação das mensagens")
    void petNameWithPercent_isSafe() {
        PetContext odd = new PetContext("PET-0002", "100%s Pet", mel.species(), 48, null, false, 10.0, false, Set.of());

        BotTurn turn = flow.next(ConversationState.initial(), "oi", odd, NO_CARE, null);

        assertThat(turn.text()).contains("100%s Pet");
    }

    @Test
    @DisplayName("assistente de LLM só roda quando o léxico está fraco e só adiciona sintomas")
    void symptomAssist_runsOnlyWhenWeak() {
        int[] calls = {0};
        ConversationFlow.SymptomAssist assist = (text, current) -> {
            calls[0]++;
            var def = TestEngines.KNOWLEDGE.symptom("dificuldade_respirar");
            return List.of(new SymptomMention(def.id(), def.label(), def.category(), 4, "(LLM)", false, 0, 0));
        };

        BotTurn strong = flow.next(ConversationState.initial(), "ela teve uma convulsão", mel, NO_CARE, assist);
        BotTurn weak = flow.next(ConversationState.initial(), "o bichinho tá com um jeito estranho de respirar", mel, NO_CARE, assist);

        assertThat(strong.triage().priority()).isEqualTo(Priority.EMERGENCIA);
        assertThat(calls[0]).isEqualTo(1);
        assertThat(weak.triage().priority()).isEqualTo(Priority.EMERGENCIA);
    }
}
