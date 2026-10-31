package br.com.zelo.config;

import br.com.zelo.ai.Species;
import br.com.zelo.domain.AppUser;
import br.com.zelo.domain.CareItem;
import br.com.zelo.domain.CareItem.Type;
import br.com.zelo.domain.Pet;
import br.com.zelo.domain.Role;
import br.com.zelo.repo.AppUserRepository;
import br.com.zelo.repo.CareItemRepository;
import br.com.zelo.repo.PetRepository;
import br.com.zelo.service.TimelineService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * Cria dados FICTÍCIOS para a demonstração (2 tutores, 1 clínica, 3 pets com plano de cuidados). Roda apenas se
 * {@code zelo.seed.enabled=true} e o banco estiver vazio. Desative (ZELO_SEED_DEMO=false) em qualquer ambiente real.
 */
@Component
@ConditionalOnProperty(prefix = "zelo.seed", name = "enabled", havingValue = "true")
public class DemoDataSeeder implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(DemoDataSeeder.class);
    private static final String DEFAULT_PASSWORD = "zelo-demo-2026";

    private final AppUserRepository users;
    private final PetRepository pets;
    private final CareItemRepository careItems;
    private final TimelineService timeline;
    private final PasswordEncoder encoder;
    private final ZeloProperties props;
    private final Clock clock;
    private final boolean secureCookies;

    public DemoDataSeeder(AppUserRepository users, PetRepository pets, CareItemRepository careItems,
                          TimelineService timeline, PasswordEncoder encoder, ZeloProperties props, Clock clock,
                          @Value("${server.servlet.session.cookie.secure:false}") boolean secureCookies) {
        this.users = users;
        this.pets = pets;
        this.careItems = careItems;
        this.timeline = timeline;
        this.encoder = encoder;
        this.props = props;
        this.clock = clock;
        this.secureCookies = secureCookies;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (secureCookies && DEFAULT_PASSWORD.equals(props.seed().demoPassword())) {
            // cookies Secure indicam implantação com HTTPS: recusar subir com conta de clínica de senha pública
            throw new IllegalStateException("Dados de demonstração ligados com a senha padrão em ambiente HTTPS. "
                    + "Defina ZELO_DEMO_PASSWORD ou desligue com ZELO_SEED_DEMO=false.");
        }
        if (users.count() > 0) {
            return;
        }
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDate today = LocalDate.now(clock);
        String hash = encoder.encode(props.seed().demoPassword());

        AppUser ana = users.save(new AppUser("ana@zelo.demo", "Ana Souza", hash, Role.TUTOR, now));
        AppUser bruno = users.save(new AppUser("bruno@zelo.demo", "Bruno Lima", hash, Role.TUTOR, now));
        users.save(new AppUser("vet@zelo.demo", "Dra. Helena Prado (Clínica Zelo)", hash, Role.CLINICA, now));

        Pet mel = pets.save(new Pet(ana, "Mel", Species.DOG, "Labrador Retriever", true, today.minusYears(4),
                new BigDecimal("27.5"), Set.of("vacinado"), now));
        Pet thor = pets.save(new Pet(ana, "Thor", Species.CAT, "Maine Coon", false, today.minusYears(2).minusMonths(3),
                new BigDecimal("5.8"), Set.of("vacinado"), now));
        Pet rex = pets.save(new Pet(bruno, "Rex", Species.DOG, "Bulldog", false, today.minusMonths(30),
                new BigDecimal("22.0"), Set.of("problemas_pele"), now));

        careItems.save(new CareItem(mel, Type.VACINA, "V10 (polivalente) — reforço anual", today.minusMonths(13), today.minusDays(12)));
        careItems.save(new CareItem(mel, Type.VACINA, "Antirrábica", today.minusMonths(11), today.plusDays(9)));
        careItems.save(new CareItem(mel, Type.VERMIFUGO, "Vermífugo", today.minusMonths(2), today.plusDays(28)));
        careItems.save(new CareItem(mel, Type.CONSULTA, "Check-up anual", today.minusMonths(10), today.plusMonths(2)));
        careItems.save(new CareItem(thor, Type.VACINA, "V4 (quádrupla felina) — reforço anual", today.minusMonths(11), today.plusDays(20)));
        careItems.save(new CareItem(thor, Type.ANTIPULGAS, "Antipulgas", today.minusDays(25), today.plusDays(5)));
        careItems.save(new CareItem(rex, Type.VACINA, "V10 (polivalente)", today.minusMonths(12), today.minusDays(3)));

        timeline.record(mel, "visit", "Consulta de rotina", "Check-up anual sem alterações. Peso estável.", "clinic", null, null);
        timeline.record(mel, "vaccine", "Vacina antirrábica aplicada", "Dose anual registrada pela clínica.", "clinic", null, null);
        timeline.record(thor, "visit", "Consulta de rotina", "Sem queixas. Orientações sobre alimentação.", "clinic", null, null);
        LOG.warn("Dados de DEMONSTRAÇÃO criados (ana@zelo.demo, bruno@zelo.demo, vet@zelo.demo). Desative com ZELO_SEED_DEMO=false.");
    }
}
