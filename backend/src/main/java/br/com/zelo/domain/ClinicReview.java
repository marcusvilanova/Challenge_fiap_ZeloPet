package br.com.zelo.domain;

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
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Revisão profissional (humano no circuito) de uma triagem compartilhada. */
@Entity
@Table(name = "clinic_review")
public class ClinicReview {

    public enum Decision {
        CONFIRMADA,
        AJUSTADA
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "triage_id", nullable = false)
    private Triage triage;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "reviewer_id", nullable = false)
    private AppUser reviewer;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Decision decision;

    @Convert(converter = PriorityConverter.class)
    @Column(name = "final_priority", nullable = false, length = 12)
    private Priority finalPriority;

    @Column(length = 1000)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ClinicReview() {
    }

    public ClinicReview(Triage triage, AppUser reviewer, Decision decision, Priority finalPriority, String note,
                        LocalDateTime createdAt) {
        this.triage = triage;
        this.reviewer = reviewer;
        this.decision = decision;
        this.finalPriority = finalPriority;
        this.note = note;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public Triage getTriage() {
        return triage;
    }

    public AppUser getReviewer() {
        return reviewer;
    }

    public Decision getDecision() {
        return decision;
    }

    public Priority getFinalPriority() {
        return finalPriority;
    }

    public String getNote() {
        return note;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
