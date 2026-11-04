package br.com.zelo.config;

import br.com.zelo.security.AppUserDetails;
import br.com.zelo.security.CsrfCookieFilter;
import br.com.zelo.security.RateLimitFilter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /** Telas do app: nenhum script/estilo embutido; só a fonte IBM Plex vem do Google Fonts. */
    private static final String APP_CSP = "default-src 'self'; img-src 'self' data: blob:; "
            + "style-src 'self' https://fonts.googleapis.com; script-src 'self'; font-src 'self' https://fonts.gstatic.com; "
            + "connect-src 'self'; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'";
    /** A interface do Swagger usa estilos/scripts embutidos; por isso tem uma CSP própria e mais frouxa. */
    private static final String DOCS_CSP = "default-src 'self'; img-src 'self' data:; style-src 'self' 'unsafe-inline'; "
            + "script-src 'self' 'unsafe-inline'; frame-ancestors 'none'";
    /** Apresentação e vídeo-guia: páginas estáticas e públicas, sem dados do usuário, com estilos/scripts embutidos. */
    private static final String PRESENTATION_CSP = "default-src 'self'; img-src 'self' data: https:; "
            + "style-src 'self' 'unsafe-inline' https://fonts.googleapis.com; script-src 'self' 'unsafe-inline'; "
            + "font-src 'self' data: https://fonts.gstatic.com; connect-src 'self'; object-src 'none'; base-uri 'self'; "
            + "form-action 'self'; frame-ancestors 'none'";
    static final String[] PRESENTATION_PATHS = {"/apresentacao", "/apresentacao.html", "/pitch", "/slides",
            "/video", "/video.html", "/demonstracao", "/*.pdf"};
    private static final String[] DASHBOARD_PATHS = {"/dashboard", "/dashboard.html", "/painel", "/tutor"};

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    Clock clock() {
        return Clock.systemUTC();
    }

    @Bean
    @Order(1)
    SecurityFilterChain docsChain(HttpSecurity http) throws Exception {
        http.securityMatcher("/swagger-ui/**", "/swagger-ui.html", "/v3/api-docs/**")
                .authorizeHttpRequests(a -> a.anyRequest().authenticated()) // contrato da API só para quem entrou
                .csrf(csrf -> csrf.disable()) // somente leitura de documentação
                .headers(h -> h.contentSecurityPolicy(csp -> csp.policyDirectives(DOCS_CSP)));
        return http.build();
    }

    @Bean
    @Order(2)
    SecurityFilterChain presentationChain(HttpSecurity http) throws Exception {
        http.securityMatcher(PRESENTATION_PATHS)
                .authorizeHttpRequests(a -> a.anyRequest().permitAll()) // material de apresentação, igual à versão online
                .csrf(csrf -> csrf.disable()) // somente GET de arquivos estáticos
                .requestCache(cache -> cache.disable())
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives(PRESENTATION_CSP))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN)));
        return http.build();
    }

    @Bean
    @Order(3)
    SecurityFilterChain appChain(HttpSecurity http, ObjectMapper mapper, Clock clock, ZeloProperties props,
                                 @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookies)
            throws Exception {
        CsrfTokenRequestAttributeHandler csrfHandler = new CsrfTokenRequestAttributeHandler();
        CookieCsrfTokenRepository csrfRepository = CookieCsrfTokenRepository.withHttpOnlyFalse();
        csrfRepository.setCookieCustomizer(cookie -> cookie.sameSite("Lax").secure(secureCookies));
        RateLimitFilter rateLimit = new RateLimitFilter(clock, props.rateLimit().perMinute(),
                props.rateLimit().loginPerMinute());

        http.csrf(csrf -> csrf.csrfTokenRepository(csrfRepository).csrfTokenRequestHandler(csrfHandler))
                .addFilterAfter(new CsrfCookieFilter(), org.springframework.security.web.csrf.CsrfFilter.class)
                // ANTES do filtro de login: ele encerra a cadeia no POST /api/auth/login e um filtro posterior nunca rodaria
                .addFilterBefore(rateLimit, UsernamePasswordAuthenticationFilter.class)
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/", "/index.html", "/css/**", "/js/**", "/fonts/**", "/img/**",
                                "/favicon.ico", "/favicon.svg", "/error").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/auth/login", "/api/auth/register").permitAll()
                        .requestMatchers("/actuator/health", "/actuator/health/**").permitAll()
                        .requestMatchers("/login").permitAll()
                        .requestMatchers("/chat.html").hasRole("TUTOR")
                        .requestMatchers(DASHBOARD_PATHS).hasRole("TUTOR")
                        .requestMatchers("/clinic.html").hasRole("CLINICA")
                        .requestMatchers("/api/clinic/**").hasRole("CLINICA")
                        .requestMatchers("/api/auth/me", "/api/auth/logout", "/api/system/**").authenticated()
                        .requestMatchers("/api/**").hasRole("TUTOR")
                        .anyRequest().authenticated())
                .formLogin(form -> form
                        .loginPage("/index.html")
                        .loginProcessingUrl("/api/auth/login")
                        .usernameParameter("email")
                        .passwordParameter("password")
                        .successHandler((req, res, auth) -> writeJson(mapper, res, HttpStatus.OK,
                                currentUser((AppUserDetails) auth.getPrincipal())))
                        .failureHandler((req, res, ex) -> writeProblem(mapper, res, HttpStatus.UNAUTHORIZED,
                                "E-mail ou senha inválidos")))
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout") // com CSRF ativo, só aceita POST
                        .logoutSuccessHandler((req, res, auth) -> res.setStatus(HttpStatus.NO_CONTENT.value()))
                        .deleteCookies("ZELO_SESSION", "XSRF-TOKEN"))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint((req, res, e) -> {
                            if (req.getRequestURI().startsWith("/api/")) {
                                new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED).commence(req, res, e);
                            } else {
                                res.sendRedirect("/index.html");
                            }
                        })
                        .accessDeniedHandler((req, res, e) -> {
                            if (req.getRequestURI().startsWith("/api/")) {
                                writeProblem(mapper, res, HttpStatus.FORBIDDEN, "Acesso negado para o seu perfil");
                            } else {
                                res.sendRedirect("/index.html?acesso=negado");
                            }
                        }))
                .headers(h -> h
                        .contentSecurityPolicy(csp -> csp.policyDirectives(APP_CSP))
                        .referrerPolicy(r -> r.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.STRICT_ORIGIN_WHEN_CROSS_ORIGIN))
                        .permissionsPolicyHeader(p -> p.policy("camera=(), microphone=(), geolocation=()"))
                        .httpStrictTransportSecurity(hsts -> hsts.maxAgeInSeconds(31_536_000).includeSubDomains(true)));
        return http.build();
    }

    private static Map<String, Object> currentUser(AppUserDetails user) {
        return Map.of("id", user.id(), "name", user.displayName(), "email", user.email(), "role", user.role().name());
    }

    private static void writeJson(ObjectMapper mapper, HttpServletResponse res, HttpStatus status, Object body)
            throws IOException {
        res.setStatus(status.value());
        res.setContentType("application/json;charset=UTF-8");
        mapper.writeValue(res.getWriter(), body);
    }

    private static void writeProblem(ObjectMapper mapper, HttpServletResponse res, HttpStatus status, String detail)
            throws IOException {
        res.setStatus(status.value());
        res.setContentType("application/problem+json;charset=UTF-8");
        mapper.writeValue(res.getWriter(), Map.of("title", status.getReasonPhrase(), "status", status.value(),
                "detail", detail));
    }
}
