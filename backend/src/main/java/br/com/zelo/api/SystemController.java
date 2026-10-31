package br.com.zelo.api;

import br.com.zelo.ai.KnowledgeBase;
import br.com.zelo.ai.TriageEngine;
import br.com.zelo.api.dto.ApiDtos.SystemStatus;
import br.com.zelo.llm.LlmClient;
import br.com.zelo.vision.VisionClient;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
@Tag(name = "Sistema")
public class SystemController {

    private final KnowledgeBase knowledge;
    private final LlmClient llm;
    private final VisionClient vision;

    public SystemController(KnowledgeBase knowledge, LlmClient llm, VisionClient vision) {
        this.knowledge = knowledge;
        this.llm = llm;
        this.vision = vision;
    }

    @Operation(summary = "Versões e disponibilidade dos componentes de IA (motor local, LLM, visão)")
    @GetMapping("/status")
    public SystemStatus status() {
        var health = vision.health();
        return new SystemStatus(knowledge.version(), TriageEngine.RULES_VERSION, llm.label(), llm.enabled(),
                health.isPresent(), health.map(VisionClient.Health::modelVersion).orElse(null));
    }
}
