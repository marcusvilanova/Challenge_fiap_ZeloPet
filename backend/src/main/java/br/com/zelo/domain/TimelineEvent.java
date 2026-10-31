package br.com.zelo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Evento da linha do tempo longitudinal do pet (triagem, consentimento, revisão, visão...). */
@Entity
@Table(name = "timeline_event")
public class TimelineEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pet_id", nullable = false)
    private Pet pet;

    @Column(nullable = false, length = 30)
    private String type;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(length = 500)
    private String summary;

    @Column(nullable = false, length = 30)
    private String source;

    @Column(name = "ref_id")
    private Long refId;

    @Lob
    @Column(name = "payload_json")
    private String payloadJson;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    protected TimelineEvent() {
    }

    public TimelineEvent(Pet pet, String type, String title, String summary, String source, Long refId,
                         String payloadJson, LocalDateTime occurredAt) {
        this.pet = pet;
        this.type = type;
        this.title = title;
        this.summary = summary;
        this.source = source;
        this.refId = refId;
        this.payloadJson = payloadJson;
        this.occurredAt = occurredAt;
    }

    public Long getId() {
        return id;
    }

    public String getType() {
        return type;
    }

    public String getTitle() {
        return title;
    }

    public String getSummary() {
        return summary;
    }

    public String getSource() {
        return source;
    }

    public Long getRefId() {
        return refId;
    }

    public LocalDateTime getOccurredAt() {
        return occurredAt;
    }
}
