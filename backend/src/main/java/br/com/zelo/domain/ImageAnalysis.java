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

/** Resultado da análise visual. Só o hash da imagem é guardado — o arquivo em si não é armazenado. */
@Entity
@Table(name = "image_analysis")
public class ImageAnalysis {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "pet_id", nullable = false)
    private Pet pet;

    @Column(nullable = false, length = 64)
    private String sha256;

    @Lob
    @Column(name = "result_json", nullable = false)
    private String resultJson;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    protected ImageAnalysis() {
    }

    public ImageAnalysis(Pet pet, String sha256, String resultJson, LocalDateTime createdAt) {
        this.pet = pet;
        this.sha256 = sha256;
        this.resultJson = resultJson;
        this.createdAt = createdAt;
    }

    public Long getId() {
        return id;
    }

    public String getSha256() {
        return sha256;
    }

    public String getResultJson() {
        return resultJson;
    }
}
