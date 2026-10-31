package br.com.zelo.llm;

import br.com.zelo.ai.PetContext;
import br.com.zelo.ai.TriageAnalysis;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Camada generativa (opcional). Regras de contrato:
 * <ul>
 *   <li>nunca lança exceção: indisponibilidade/erro viram resultado vazio e o motor local assume;</li>
 *   <li>recebe só os dados mínimos (espécie, idade, relato, sintomas) — sem dados do tutor;</li>
 *   <li>pode ADICIONAR sintomas e REESCREVER a resposta, mas nunca reduz a prioridade do motor determinístico.</li>
 * </ul>
 */
public interface LlmClient {

    boolean enabled();

    /** Rótulo para auditoria: modelo usado ou "local-fallback". */
    String label();

    /** Ids de sintomas (do vocabulário permitido) que o relato descreve como presentes; vazio se indisponível. */
    List<String> extractSymptoms(String userText, PetContext pet, Map<String, String> allowedIdToLabel);

    /** Reescreve o rascunho seguro da triagem de forma mais natural e personalizada; vazio se indisponível. */
    Optional<String> rewriteReply(String userText, PetContext pet, TriageAnalysis analysis, String draft);
}
