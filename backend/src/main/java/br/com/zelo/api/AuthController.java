package br.com.zelo.api;

import br.com.zelo.api.dto.ApiDtos.RegisterRequest;
import br.com.zelo.api.dto.ApiDtos.UserResponse;
import br.com.zelo.domain.AppUser;
import br.com.zelo.domain.Role;
import br.com.zelo.repo.AppUserRepository;
import br.com.zelo.security.AppUserDetails;
import br.com.zelo.service.ConflictException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;

/** Login e logout são tratados pelo Spring Security (POST /api/auth/login, POST /api/auth/logout). */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticação")
public class AuthController {

    private final AppUserRepository users;
    private final PasswordEncoder encoder;
    private final Clock clock;

    public AuthController(AppUserRepository users, PasswordEncoder encoder, Clock clock) {
        this.users = users;
        this.encoder = encoder;
        this.clock = clock;
    }

    @Operation(summary = "Cria a conta de um tutor")
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public UserResponse register(@Valid @RequestBody RegisterRequest request) {
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmailIgnoreCase(email)) {
            throw new ConflictException("Não foi possível cadastrar com este e-mail");
        }
        try {
            AppUser saved = users.saveAndFlush(new AppUser(email, request.name().trim(),
                    encoder.encode(request.password()), Role.TUTOR, LocalDateTime.now(clock)));
            return new UserResponse(saved.getId(), saved.getDisplayName(), saved.getEmail(), saved.getRole().name());
        } catch (DataIntegrityViolationException e) {
            throw new ConflictException("Não foi possível cadastrar com este e-mail");
        }
    }

    @Operation(summary = "Usuário autenticado")
    @GetMapping("/me")
    public UserResponse me(@AuthenticationPrincipal AppUserDetails me) {
        return new UserResponse(me.id(), me.displayName(), me.email(), me.role().name());
    }
}
