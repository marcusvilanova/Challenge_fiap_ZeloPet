package br.com.zelo.domain;

import br.com.zelo.ai.Species;
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

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.Period;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "pet")
public class Pet {

    private static final String PUBLIC_ID_PREFIX = "PET-";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "owner_id", nullable = false)
    private AppUser owner;

    @Column(nullable = false, length = 80)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Species species;

    @Column(length = 80)
    private String breed;

    @Column(nullable = false)
    private boolean female;

    @Column(name = "birth_date")
    private LocalDate birthDate;

    @Column(name = "weight_kg", precision = 6, scale = 2)
    private BigDecimal weightKg;

    @Column(name = "history_tags", length = 400)
    private String historyTags;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected Pet() {
    }

    public Pet(AppUser owner, String name, Species species, String breed, boolean female, LocalDate birthDate,
               BigDecimal weightKg, Set<String> historyTags, LocalDateTime createdAt) {
        this.owner = owner;
        this.name = name;
        this.species = species;
        this.breed = breed;
        this.female = female;
        this.birthDate = birthDate;
        this.weightKg = weightKg;
        this.historyTags = String.join(",", historyTags);
        this.createdAt = createdAt;
    }

    /** Edição dos dados pelo tutor (o dono e a data de cadastro não mudam). */
    public void update(String name, Species species, String breed, boolean female, LocalDate birthDate,
                       BigDecimal weightKg, Set<String> historyTags) {
        this.name = name;
        this.species = species;
        this.breed = breed;
        this.female = female;
        this.birthDate = birthDate;
        this.weightKg = weightKg;
        this.historyTags = String.join(",", historyTags);
    }

    public static String publicId(Long id) {
        return PUBLIC_ID_PREFIX + String.format("%04d", id);
    }

    /** Converte "PET-0007" (ou "7") no id numérico; lança {@link IllegalArgumentException} se inválido. */
    public static long parsePublicId(String value) {
        String raw = value == null ? "" : value.trim();
        String digits = raw.regionMatches(true, 0, PUBLIC_ID_PREFIX, 0, PUBLIC_ID_PREFIX.length())
                ? raw.substring(PUBLIC_ID_PREFIX.length()) : raw;
        if (digits.isEmpty() || !digits.chars().allMatch(Character::isDigit) || digits.length() > 12) {
            throw new IllegalArgumentException("Identificador de pet inválido");
        }
        return Long.parseLong(digits);
    }

    public int ageInMonths(LocalDate today) {
        if (birthDate == null) {
            return -1;
        }
        Period period = Period.between(birthDate, today);
        return period.getYears() * 12 + period.getMonths();
    }

    public Set<String> historyTagSet() {
        if (historyTags == null || historyTags.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(historyTags.split(",")).map(String::trim).filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public Long getId() {
        return id;
    }

    public String getPublicId() {
        return publicId(id);
    }

    public AppUser getOwner() {
        return owner;
    }

    public String getName() {
        return name;
    }

    public Species getSpecies() {
        return species;
    }

    public String getBreed() {
        return breed;
    }

    public boolean isFemale() {
        return female;
    }

    public LocalDate getBirthDate() {
        return birthDate;
    }

    public BigDecimal getWeightKg() {
        return weightKg;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
