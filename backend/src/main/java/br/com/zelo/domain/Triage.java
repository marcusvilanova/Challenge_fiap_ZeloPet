package br.com.zelo.domain;

import br.com.zelo.ai.NextAction;
import br.com.zelo.ai.Priority;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Triagem preliminar persistida junto da resposta estruturada da IA (rastreabilidade). */
@Entity
@Table(name = "triage")
public class Triage {

    private static final String PUBLIC_ID_PREFIX = "TRI-";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pet_id", nullable = false)
    private Pet pet;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "session_id")
    private ChatSession session;

    @Column(nullable = false, length = 20)
    private String source;

    @Lob
    @Column(name = "input_text", nullable = false)
    private String inputText;

    @Lob
    @Column(name = "result_json", nullable = false)
    private String resultJson;

    @Convert(converter = PriorityConverter.class)
    @Column(nullable = false, length = 12)
    private Priority priority;

    @Convert(converter = NextActionConverter.class)
    @Column(name = "next_action", nullable = false, length = 40)
    private NextAction nextAction;

    @Column(nullable = false, precision = 4, scale = 2)
    private BigDecimal confidence;

    @Column(name = "model_version", nullable = false, length = 60)
    private String modelVersion;

    @Column(name = "human_review_required", nullable = false)
    private boolean humanReviewRequired;

    @Column(name = "shared_with_clinic", nullable = false)
    private boolean sharedWithClinic;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Triage() {
    }

    public Triage(Pet pet, ChatSession session, String source, String inputText, String resultJson, Priority priority,
                  NextAction nextAction, double confidence, String modelVersion, boolean humanReviewRequired,
                  LocalDateTime createdAt) {
        this.pet = pet;
        this.session = session;
        this.source = source;
        this.inputText = inputText;
        this.resultJson = resultJson;
        this.priority = priority;
        this.nextAction = nextAction;
        this.confidence = BigDecimal.valueOf(confidence);
        this.modelVersion = modelVersion;
        this.humanReviewRequired = humanReviewRequired;
        this.sharedWithClinic = false;
        this.createdAt = createdAt;
    }

    public static String publicId(Long id) {
        return PUBLIC_ID_PREFIX + String.format("%06d", id);
    }

    public static long parsePublicId(String value) {
        String raw = value == null ? "" : value.trim();
        String digits = raw.regionMatches(true, 0, PUBLIC_ID_PREFIX, 0, PUBLIC_ID_PREFIX.length())
                ? raw.substring(PUBLIC_ID_PREFIX.length()) : raw;
        if (digits.isEmpty() || !digits.chars().allMatch(Character::isDigit) || digits.length() > 12) {
            throw new IllegalArgumentException("Identificador de triagem inválido");
        }
        return Long.parseLong(digits);
    }

    public void markShared() {
        this.sharedWithClinic = true;
    }

    public void replaceResultJson(String json) {
        this.resultJson = json;
    }

    public Long getId() {
        return id;
    }

    public String getPublicId() {
        return publicId(id);
    }

    public Pet getPet() {
        return pet;
    }

    public ChatSession getSession() {
        return session;
    }

    public String getSource() {
        return source;
    }

    public String getInputText() {
        return inputText;
    }

    public String getResultJson() {
        return resultJson;
    }

    public Priority getPriority() {
        return priority;
    }

    public NextAction getNextAction() {
        return nextAction;
    }

    public BigDecimal getConfidence() {
        return confidence;
    }

    public String getModelVersion() {
        return modelVersion;
    }

    public boolean isHumanReviewRequired() {
        return humanReviewRequired;
    }

    public boolean isSharedWithClinic() {
        return sharedWithClinic;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
