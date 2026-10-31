package br.com.zelo.chat;

import br.com.zelo.ai.PetContext;
import br.com.zelo.ai.Priority;
import br.com.zelo.ai.SymptomMention;
import br.com.zelo.ai.TriageAnalysis;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Voz do Zelo. Textos em português do Brasil, personalizados (nome, gênero, espécie, contexto),
 * sempre sem diagnóstico e sem indicação de medicamentos. É também o rascunho seguro que o LLM,
 * quando disponível, apenas reescreve.
 */
public final class ReplyComposer {

    private static final int MAX_LISTED_SYMPTOMS = 5;

    private static final Map<String, String> WATCH_FOR = Map.of(
            "digestivo", "Procure a clínica antes se houver sangue no vômito ou nas fezes, vômitos que não param, barriga inchada ou se %s ficar muito abatid%s.",
            "respiratorio", "Procure atendimento na hora se %s respirar com esforço, de boca aberta, ou se a língua e as gengivas ficarem arroxeadas.",
            "pele", "Evite que %s lamba ou coce a região e procure a clínica se a lesão aumentar, sangrar ou tiver mau cheiro.",
            "urinario", "Observe se %s consegue urinar e anote a frequência. Se não sair xixi, é urgência.",
            "neurologico", "Se houver convulsão, perda de equilíbrio ou desorientação, vá a um pronto-atendimento.",
            "ocular", "Evite que %s coce o olho; se ficar fechado, opaco ou muito dolorido, procure atendimento hoje.",
            "locomotor", "Limite o esforço, evite escadas e procure atendimento se %s não apoiar a pata ou se houver inchaço.");

    private static final Map<String, String> FIRST_AID = Map.ofEntries(
            Map.entry("convulsao", "Afaste objetos que possam machucar, não coloque a mão na boca, mantenha o ambiente calmo e anote a duração."),
            Map.entry("dificuldade_respirar", "Mantenha %s calm%s, em local fresco e arejado, e evite qualquer esforço."),
            Map.entry("ingestao_toxica", "Leve a embalagem, planta ou produto ingerido e não provoque vômito sem orientação veterinária."),
            Map.entry("nao_urina", "Não espere: a obstrução urinária pode ser fatal em poucas horas."),
            Map.entry("dificuldade_urinar", "Não espere: em gatos, dificuldade para urinar pode virar obstrução e ser fatal em poucas horas."),
            Map.entry("golpe_calor", "Leve a um local fresco e molhe o corpo com água em temperatura ambiente (não gelada) durante o transporte."),
            Map.entry("trauma", "Movimente %s o mínimo possível e, se houver sangramento, pressione com um pano limpo."),
            Map.entry("sangramento_intenso", "Pressione o local com um pano limpo e mantenha a pressão durante o transporte."),
            Map.entry("picada_peconhento", "Mantenha %s calm%s e, se possível, fotografe ou anote o animal. Não faça torniquete nem corte o local."),
            Map.entry("abdomen_distendido", "Não ofereça comida nem água."),
            Map.entry("abdomen_agudo", "Não ofereça comida nem água."));

    public String greeting(PetContext pet, String careHighlight) {
        String base = "Oi! Eu sou a Zelo, assistente de cuidado " + of(pet) + ". Posso entender o que " + pet.pronoun()
                + " está sentindo e orientar o próximo passo, ou mostrar os cuidados que estão chegando.";
        return careHighlight == null ? base : base + "\n\n" + careHighlight;
    }

    public String help(PetContext pet) {
        return "Eu faço uma triagem preliminar: entendo o que você observa " + inThe(pet) + ", indico a prioridade e o próximo passo, "
                + "e posso avisar a clínica se você autorizar.\n\n"
                + "Não faço diagnóstico nem indico remédios: isso é com o médico-veterinário. "
                + "Também mostro as vacinas e os cuidados que estão chegando.";
    }

    public String thanks(PetContext pet) {
        return "Por nada! Se " + pet.article() + " " + pet.name() + " piorar ou surgir algo novo, é só me contar.";
    }

    public String farewell(PetContext pet) {
        return "Até logo! Cuide bem " + of(pet) + ". Se algo mudar, volte aqui.";
    }

    public String askForReport(PetContext pet) {
        return "Claro. O que você notou " + inThe(pet) + "? Pode escrever do seu jeito, por exemplo: \"está vomitando e quieto desde ontem\".";
    }

    public String medicationRefusal() {
        return "Não posso indicar remédios nem doses. Vários medicamentos humanos, como dipirona, paracetamol e ibuprofeno, "
                + "são perigosos para cães e gatos, e a dose certa depende do peso e do quadro. Quem define isso com segurança é o médico-veterinário.";
    }

    public String carePlan(PetContext pet, List<String> lines) {
        if (lines.isEmpty()) {
            return "Ainda não há cuidados cadastrados " + of(pet) + ". A clínica pode registrá-los na próxima consulta.";
        }
        return "Estes são os próximos cuidados " + of(pet) + ":\n" + lines.stream().map(l -> "• " + l).collect(Collectors.joining("\n"));
    }

    public String askDuration(PetContext pet, TriageAnalysis a) {
        return "Entendi: " + summary(a.active()) + ". Desde quando " + pet.pronoun() + " está assim?";
    }

    public String askIntake(PetContext pet) {
        return pet.pronoun().substring(0, 1).toUpperCase(Locale.ROOT) + pet.pronoun().substring(1)
                + " está comendo e bebendo água normalmente?";
    }

    public String askBreathing(PetContext pet) {
        return "Como está a respiração " + of(pet) + "? Ela está tranquila ou " + pet.pronoun() + " respira com esforço?";
    }

    public String clarify(PetContext pet) {
        return "Não consegui entender isso como um sinal de saúde " + of(pet) + ". Pode contar com outras palavras o que "
                + pet.pronoun() + " está sentindo?\n\n"
                + "Se houver falta de ar, convulsão, desmaio, sangramento forte, suspeita de intoxicação ou se " + pet.pronoun()
                + " não conseguir urinar, procure um pronto-atendimento veterinário agora, sem esperar.";
    }

    public String noTriageToShare(PetContext pet) {
        return "Ainda não tenho uma triagem para compartilhar. Conte o que " + pet.pronoun() + " está sentindo e eu preparo o resumo.";
    }

    public String shareGranted(PetContext pet) {
        return "Pronto. Enviei o resumo " + of(pet) + " para a clínica. Um profissional vai revisar e você pode revogar esse compartilhamento "
                + "quando quiser. Se " + pet.pronoun() + " piorar, não espere resposta: procure atendimento.";
    }

    public String shareDeclined() {
        return "Tudo bem, não compartilhei nada com a clínica. A triagem fica salva só no seu histórico, e você pode enviá-la depois.";
    }

    /** Mensagem final da triagem: prioridade, o que foi identificado, por quê, o que observar e o aviso de segurança. */
    public String triage(TriageAnalysis a, PetContext pet, String careHighlight, boolean askToShare) {
        StringBuilder sb = new StringBuilder(headline(a, pet));
        String firstAid = firstAid(a, pet);
        if (!firstAid.isEmpty()) {
            sb.append("\n\n").append(firstAid);
        }
        // sintomas, fatores de contexto e confiança ficam no cartão estruturado; aqui só a razão principal
        if (!a.reasons().isEmpty()) {
            sb.append("\n\nPor quê: ").append(a.reasons().get(0)).append(".");
        }
        if (a.priority().level() < Priority.EMERGENCIA.level()) {
            sb.append("\n\n").append(watchFor(a, pet));
        }
        if (careHighlight != null && a.priority().level() <= Priority.MEDIA.level()) {
            sb.append("\n\n").append(careHighlight);
        }
        sb.append("\n\nIsto é uma triagem preliminar e não substitui a avaliação de um médico-veterinário.");
        if (askToShare) {
            sb.append(a.emergency()
                    ? " Posso avisar a clínica agora enquanto você se desloca?"
                    : " Quer que eu envie este resumo para a clínica?");
        }
        return sb.toString();
    }

    private String headline(TriageAnalysis a, PetContext pet) {
        return switch (a.priority()) {
            case EMERGENCIA -> "Atenção: isso pode ser uma emergência. Leve " + pet.article() + " " + pet.name()
                    + " agora a um pronto-atendimento veterinário. Ligue antes para avisarem a equipe e não ofereça comida, água ou remédios sem orientação.";
            case ALTA -> "Pelo que você descreveu, o ideal é falar com a clínica ainda hoje. Não vale esperar para ver se melhora sozinho.";
            case MEDIA -> "Vale agendar uma consulta nas próximas 24 a 72 horas. Se piorar antes, antecipe.";
            case BAIXA -> a.hasSymptoms()
                    ? "Pelo que você descreveu, parece algo leve. Observe por 24 a 48 horas e registre como " + pet.pronoun() + " evolui."
                    : "Não identifiquei sinais de alerta no que você contou. Continue observando e me avise se algo mudar.";
        };
    }

    private String firstAid(TriageAnalysis a, PetContext pet) {
        String ending = pet.female() ? "a" : "o";
        for (String id : a.redFlagIds()) {
            String template = FIRST_AID.get(id);
            if (template != null) {
                return template.formatted(pet.pronoun(), ending);
            }
        }
        return a.emergency() ? "Mantenha " + pet.pronoun() + " calm" + ending + " e aquecid" + ending + " até chegar." : "";
    }

    private String watchFor(TriageAnalysis a, PetContext pet) {
        String ending = pet.female() ? "a" : "o";
        String category = a.active().stream().map(SymptomMention::category).filter(WATCH_FOR::containsKey).findFirst().orElse("");
        String template = WATCH_FOR.get(category);
        if (template == null) {
            return "Se " + pet.pronoun() + " piorar, parar de comer ou beber, ou surgir qualquer sinal novo, me conte ou procure a clínica.";
        }
        return template.formatted(pet.pronoun(), ending);
    }

    private static String summary(List<SymptomMention> mentions) {
        return mentions.stream().limit(MAX_LISTED_SYMPTOMS).map(m -> m.label().toLowerCase(Locale.ROOT))
                .collect(Collectors.joining(", "));
    }

    private static String of(PetContext pet) {
        return (pet.female() ? "da " : "do ") + pet.name();
    }

    private static String inThe(PetContext pet) {
        return pet.female() ? "na " + pet.name() : "no " + pet.name();
    }
}
