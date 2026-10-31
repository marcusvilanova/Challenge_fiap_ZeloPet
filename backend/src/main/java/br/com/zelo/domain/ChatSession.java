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

@Entity
@Table(name = "chat_session")
public class ChatSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pet_id", nullable = false)
    private Pet pet;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private AppUser user;

    @Lob
    @Column(name = "state_json", nullable = false)
    private String stateJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    protected ChatSession() {
    }

    public ChatSession(Pet pet, AppUser user, String stateJson, LocalDateTime now) {
        this.pet = pet;
        this.user = user;
        this.stateJson = stateJson;
        this.createdAt = now;
        this.updatedAt = now;
    }

    public void updateState(String newStateJson, LocalDateTime now) {
        this.stateJson = newStateJson;
        this.updatedAt = now;
    }

    public Long getId() {
        return id;
    }

    public Pet getPet() {
        return pet;
    }

    public AppUser getUser() {
        return user;
    }

    public String getStateJson() {
        return stateJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
