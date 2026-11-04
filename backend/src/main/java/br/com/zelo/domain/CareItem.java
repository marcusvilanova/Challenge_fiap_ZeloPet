package br.com.zelo.domain;

import jakarta.persistence.Column;
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

import java.time.LocalDate;

/** Item do plano de cuidados (Ciclo Zelo): vacina, vermífugo, antipulgas, retorno... */
@Entity
@Table(name = "care_item")
public class CareItem {

    public enum Type {
        VACINA(365, "vaccine"),
        VERMIFUGO(90, "medication"),
        ANTIPULGAS(30, "medication"),
        CONSULTA(365, "visit");

        private final int defaultIntervalDays;
        private final String timelineType;

        Type(int defaultIntervalDays, String timelineType) {
            this.defaultIntervalDays = defaultIntervalDays;
            this.timelineType = timelineType;
        }

        /** Intervalo usado para sugerir a próxima data quando o tutor marca o cuidado como feito. */
        public int defaultIntervalDays() {
            return defaultIntervalDays;
        }

        /** Tipo do evento registrado na linha do tempo quando o cuidado é feito. */
        public String timelineType() {
            return timelineType;
        }
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pet_id", nullable = false)
    private Pet pet;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Type type;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(name = "last_done")
    private LocalDate lastDone;

    @Column(name = "next_due", nullable = false)
    private LocalDate nextDue;

    protected CareItem() {
    }

    public CareItem(Pet pet, Type type, String name, LocalDate lastDone, LocalDate nextDue) {
        this.pet = pet;
        this.type = type;
        this.name = name;
        this.lastDone = lastDone;
        this.nextDue = nextDue;
    }

    /** Registra que o cuidado foi feito em {@code doneOn} e agenda o próximo. */
    public void markDone(LocalDate doneOn, LocalDate next) {
        this.lastDone = doneOn;
        this.nextDue = next;
    }

    public Long getId() {
        return id;
    }

    public Pet getPet() {
        return pet;
    }

    public Type getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    public LocalDate getLastDone() {
        return lastDone;
    }

    public LocalDate getNextDue() {
        return nextDue;
    }
}
