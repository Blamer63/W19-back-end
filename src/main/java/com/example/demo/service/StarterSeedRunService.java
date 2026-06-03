package com.example.demo.service;

import com.example.demo.entity.Profile;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Locale;

@Slf4j
@Service
@RequiredArgsConstructor
public class StarterSeedRunService {

    private static final String SEED_KEY = "NEW_USER_STARTER_V1";
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_RUNNING = "RUNNING";
    private static final String STATUS_COMPLETED = "COMPLETED";

    private final JdbcTemplate jdbcTemplate;
    private final DemoLearningSeedService demoLearningSeedService;

    @Value("${app.starter-seed.enabled:false}")
    private boolean starterSeedEnabled;

    @PostConstruct
    public void init() {
        if (!starterSeedEnabled) {
            return;
        }
        try {
            ensureSeedTable();
            log.info("starter-seed: user_starter_seed_runs table ready");
        } catch (Exception e) {
            log.warn("starter-seed: failed to ensure seed table on startup — seeding will be skipped", e);
            starterSeedEnabled = false;
        }
    }

    @Transactional
    public void createPendingForNewUser(Profile profile) {
        if (!starterSeedEnabled) {
            return;
        }

        ensureSeedTable();
        Integer existing = jdbcTemplate.queryForObject("""
                select count(*)
                from user_starter_seed_runs
                where profile_id = ? and seed_key = ?
                """, Integer.class, profile.getId(), SEED_KEY);
        if (existing != null && existing > 0) {
            log.debug("starter-seed: PENDING record already exists for profile {}", profile.getId());
            return;
        }

        jdbcTemplate.update("""
                insert into user_starter_seed_runs
                    (profile_id, seed_key, status, created_at, completed_at)
                values (?, ?, ?, ?, null)
                """, profile.getId(), SEED_KEY, STATUS_PENDING, Timestamp.from(Instant.now()));
        log.info("starter-seed: PENDING record created for new profile {}", profile.getId());
    }

    // REQUIRES_NEW: runs in its own transaction, independent of the caller's language-update transaction.
    // If seeding throws, only this transaction rolls back (restoring PENDING for retry).
    // The caller must catch the exception to prevent the language-update transaction from rolling back.
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void seedIfPending(Profile profile, List<String> learningLanguageCodes) {
        if (!starterSeedEnabled || learningLanguageCodes == null) {
            return;
        }

        List<String> normalizedCodes = learningLanguageCodes.stream()
                .map(this::normalize)
                .filter(code -> !code.isBlank())
                .distinct()
                .toList();
        if (normalizedCodes.isEmpty()) {
            return;
        }

        ensureSeedTable();
        int acquired = jdbcTemplate.update("""
                update user_starter_seed_runs
                set status = ?
                where profile_id = ?
                  and seed_key = ?
                  and status = ?
                """, STATUS_RUNNING, profile.getId(), SEED_KEY, STATUS_PENDING);
        if (acquired == 0) {
            log.debug("starter-seed: no PENDING record for profile {} — already seeded or not yet registered",
                    profile.getId());
            return;
        }

        log.info("starter-seed: starting seed for profile {} languages {}", profile.getId(), normalizedCodes);
        demoLearningSeedService.seedForLearningLanguages(profile, normalizedCodes);

        jdbcTemplate.update("""
                update user_starter_seed_runs
                set status = ?, completed_at = ?
                where profile_id = ?
                  and seed_key = ?
                  and status = ?
                """, STATUS_COMPLETED, Timestamp.from(Instant.now()), profile.getId(), SEED_KEY, STATUS_RUNNING);
        log.info("starter-seed: completed for profile {}", profile.getId());
    }

    private void ensureSeedTable() {
        jdbcTemplate.execute("""
                create table if not exists user_starter_seed_runs (
                    profile_id uuid not null,
                    seed_key varchar(80) not null,
                    status varchar(20) not null,
                    created_at timestamp not null,
                    completed_at timestamp,
                    primary key (profile_id, seed_key),
                    constraint fk_user_starter_seed_runs_profile
                        foreign key (profile_id) references profiles(id) on delete cascade
                )
                """);
    }

    private String normalize(String code) {
        return code == null ? "" : code.trim().toLowerCase(Locale.ROOT);
    }
}
