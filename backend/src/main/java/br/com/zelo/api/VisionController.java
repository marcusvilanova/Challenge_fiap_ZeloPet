package br.com.zelo.api;

import br.com.zelo.api.dto.VisionResult;
import br.com.zelo.security.AppUserDetails;
import br.com.zelo.vision.VisionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/vision")
@Tag(name = "Visão computacional (experimental)")
public class VisionController {

    private final VisionService vision;

    public VisionController(VisionService vision) {
        this.vision = vision;
    }

    @Operation(summary = "Classificação experimental de uma foto de pele (somente cães). Guarda só o hash da imagem.")
    @PostMapping(value = "/analyze", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public VisionResult analyze(@AuthenticationPrincipal AppUserDetails me,
                                @RequestParam("pet_id") String petId,
                                @RequestParam(value = "session_id", required = false) Long sessionId,
                                @RequestParam("file") MultipartFile file) {
        return vision.analyze(me.id(), petId, sessionId, file);
    }
}
