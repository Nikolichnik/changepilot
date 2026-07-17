package com.changepilot.change.service;

import com.changepilot.change.domain.AcceptanceCriterion;
import com.changepilot.change.domain.ChangeStatus;
import com.changepilot.change.domain.EngineeringChange;
import com.changepilot.change.domain.RiskLevel;
import com.changepilot.change.persistence.EngineeringChangeRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Component
public class DemoDataInitializer implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoDataInitializer.class);

    private final EngineeringChangeRepository repository;
    private final Clock clock;
    private final boolean enabled;

    public DemoDataInitializer(EngineeringChangeRepository repository,
                               Clock clock,
                               @Value("${changepilot.demo-data.enabled:true}") boolean enabled) {
        this.repository = repository;
        this.clock = clock;
        this.enabled = enabled;
    }

    @Override
    public void run(ApplicationArguments args) {
        log.info("ChangePilot API startup");
        if (!enabled || repository.count() > 0) {
            return;
        }

        repository.save(seed("Draft API Cleanup", "Prepare API cleanup plan for legacy endpoints.", RiskLevel.LOW,
                ChangeStatus.DRAFT, List.of("gateway", "legacy-api"),
                List.of(new CriterionSeed("Document breaking changes", false), new CriterionSeed("Review rollback", false))));

        repository.save(seed("Production Firewall Rollout", "Roll out updated firewall rules to production.", RiskLevel.HIGH,
                ChangeStatus.IN_PROGRESS, List.of("network-edge", "ops-dashboard"),
                List.of(new CriterionSeed("Stage rules in pre-prod", true), new CriterionSeed("Validate monitoring", false))));

        repository.save(seed("Verified Observability Upgrade", "Upgrade observability agents across services.", RiskLevel.MEDIUM,
                ChangeStatus.VERIFIED, List.of("metrics-agent", "alerting"),
                List.of(new CriterionSeed("Deploy collectors", true), new CriterionSeed("Verify dashboards", true))));

        log.info("Seeded demo engineering changes");
    }

    private EngineeringChange seed(String title,
                                   String description,
                                   RiskLevel risk,
                                   ChangeStatus status,
                                   List<String> components,
                                   List<CriterionSeed> criteria) {
        Instant now = Instant.now(clock);
        EngineeringChange change = new EngineeringChange(UUID.randomUUID(), title, description, risk, status, now, now);
        change.replaceAffectedComponents(components);
        change.replaceAcceptanceCriteria(criteria.stream()
                .map(seed -> new AcceptanceCriterion(UUID.randomUUID(), seed.text(), seed.completed()))
                .toList());
        return change;
    }

    private record CriterionSeed(String text, boolean completed) {
    }
}
