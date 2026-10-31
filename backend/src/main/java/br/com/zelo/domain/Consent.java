package br.com.zelo.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

/** Consentimento do tutor para compartilhar a triagem do pet com a clínica (LGPD: escopo + versão + revogação). */
@Entity
@Table(name = "consent")
public class Consent {

    public static final String SCOPE_TRIAGE_AND_CLINIC = "triage_and_clinic_sharing";
    public static final String CURRENT_VERSION = "v1-2026-10";
    public static final String DEFAULT_RECIPIENT = "clinica-zelo-demo";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pet_id", nullable = false)
    private Pet pet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "granted_by", nullable = false)
    private AppUser grantedBy;

    @Column(nullable = false, length = 60)
    private String scope;

    @Column(nullable = false, length = 20)
    private String version;

    @Column(nullable = false, length = 80)
    private String recipient;

    @Column(name = "granted_at", nullable = false)
    private LocalDateTime grantedAt;

    @Column(name = "revoked_at")
    private LocalDateTime revokedAt;

    protected Consent() {
    }

    public Consent(Pet pet, AppUser grantedBy, String scope, String version, String recipient, LocalDateTime grantedAt) {
        this.pet = pet;
        this.grantedBy = grantedBy;
        this.scope = scope;
        this.version = version;
        this.recipient = recipient;
        this.grantedAt = grantedAt;
    }

    public void revoke(LocalDateTime now) {
        this.revokedAt = now;
    }

    public boolean isActive() {
        return revokedAt == null;
    }

    public Long getId() {
        return id;
    }

    public Pet getPet() {
        return pet;
    }

    public String getScope() {
        return scope;
    }

    public String getVersion() {
        return version;
    }

    public String getRecipient() {
        return recipient;
    }

    public LocalDateTime getGrantedAt() {
        return grantedAt;
    }

    public LocalDateTime getRevokedAt() {
        return revokedAt;
    }
}
