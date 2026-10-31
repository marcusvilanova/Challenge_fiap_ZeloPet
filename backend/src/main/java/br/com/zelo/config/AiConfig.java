package br.com.zelo.config;

import br.com.zelo.ai.KnowledgeBase;
import br.com.zelo.ai.PriorityEngine;
import br.com.zelo.ai.SymptomExtractor;
import br.com.zelo.ai.TriageEngine;
import br.com.zelo.ai.TypoCorrector;
import br.com.zelo.chat.ConversationFlow;
import br.com.zelo.llm.AnthropicLlmClient;
import br.com.zelo.llm.LlmClient;
import br.com.zelo.llm.NoopLlmClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Monta os componentes de IA (núcleo determinístico em Java puro + LLM opcional). */
@Configuration
public class AiConfig {

    private static final Logger LOG = LoggerFactory.getLogger(AiConfig.class);

    @Bean
    KnowledgeBase knowledgeBase() {
        KnowledgeBase knowledge = KnowledgeBase.loadDefault();
        LOG.info("Base de conhecimento v{} carregada: {} sintomas (D2: {} registros limpos)",
                knowledge.version(), knowledge.symptoms().size(), knowledge.d2().rowsClean());
        return knowledge;
    }

    @Bean
    TriageEngine triageEngine(KnowledgeBase knowledge) {
        TypoCorrector corrector = new TypoCorrector(knowledge.typoVocabulary(), knowledge::recognizesWord);
        return new TriageEngine(new SymptomExtractor(knowledge, corrector), new PriorityEngine());
    }

    @Bean
    ConversationFlow conversationFlow(TriageEngine engine, KnowledgeBase knowledge) {
        return new ConversationFlow(engine, knowledge);
    }

    @Bean
    LlmClient llmClient(ZeloProperties props) {
        if (props.llm().enabled()) {
            LOG.info("LLM habilitado: modelo {}", props.llm().model());
            return new AnthropicLlmClient(props.llm());
        }
        LOG.info("LLM desabilitado (sem ANTHROPIC_API_KEY ou provider=local): usando o motor local");
        return new NoopLlmClient();
    }
}
