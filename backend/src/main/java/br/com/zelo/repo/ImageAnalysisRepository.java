package br.com.zelo.repo;

import br.com.zelo.domain.ImageAnalysis;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ImageAnalysisRepository extends JpaRepository<ImageAnalysis, Long> {
}
