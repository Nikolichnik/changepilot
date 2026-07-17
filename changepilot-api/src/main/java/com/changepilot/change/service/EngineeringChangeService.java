package com.changepilot.change.service;

import com.changepilot.change.domain.AcceptanceCriterion;
import com.changepilot.change.domain.ChangeStatus;
import com.changepilot.change.domain.EngineeringChange;
import com.changepilot.change.persistence.EngineeringChangeRepository;
import org.hibernate.Hibernate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class EngineeringChangeService {

    private static final Logger log = LoggerFactory.getLogger(EngineeringChangeService.class);
    private static final Sort UPDATED_DESC = Sort.by(Sort.Direction.DESC, "updatedAt");
    private static final Map<ChangeStatus, List<ChangeStatus>> TRANSITIONS = buildTransitions();

    private final EngineeringChangeRepository repository;
    private final Clock clock;

    public EngineeringChangeService(EngineeringChangeRepository repository, Clock clock) {
        this.repository = repository;
        this.clock = clock;
    }

    public List<EngineeringChange> list(ChangeStatus status) {
        List<EngineeringChange> changes = status != null
                ? repository.findAllByStatus(status, UPDATED_DESC)
                : repository.findAll(UPDATED_DESC);
        changes.forEach(change -> Hibernate.initialize(change.getAcceptanceCriteria()));
        return changes;
    }

    public EngineeringChange get(UUID id) {
        return loadDetailed(id);
    }

    @Transactional
    public EngineeringChange create(EngineeringChangeUpsertCommand command) {
        Instant now = Instant.now(clock);
        EngineeringChange change = new EngineeringChange(UUID.randomUUID(),
                normalizeText(command.title(), "title"),
                normalizeText(command.description(), "description"),
                Objects.requireNonNull(command.risk(), "risk is required"),
                ChangeStatus.DRAFT,
                now,
                now);
        change.replaceAffectedComponents(normalizeAffectedComponents(command.affectedComponents()));
        change.replaceAcceptanceCriteria(buildCriteria(command.criteria(), List.of(), false));
        EngineeringChange saved = repository.save(change);
        initializeDetails(saved);
        log.info("Created engineering change {} with status {}", saved.getId(), saved.getStatus());
        return saved;
    }

    @Transactional
    public EngineeringChange update(UUID id, EngineeringChangeUpsertCommand command) {
        EngineeringChange change = loadDetailed(id);
        rejectIfDone(change, "Completed changes are read-only");

        change.setTitle(normalizeText(command.title(), "title"));
        change.setDescription(normalizeText(command.description(), "description"));
        change.setRisk(Objects.requireNonNull(command.risk(), "risk is required"));
        change.replaceAffectedComponents(normalizeAffectedComponents(command.affectedComponents()));

        boolean lockedCriteria = change.getStatus() == ChangeStatus.VERIFIED;
        change.replaceAcceptanceCriteria(buildCriteria(command.criteria(), change.getAcceptanceCriteria(), lockedCriteria));
        touch(change);
        EngineeringChange saved = repository.save(change);
        initializeDetails(saved);
        return saved;
    }

    @Transactional
    public EngineeringChange updateCriterionCompletion(UUID changeId, UUID criterionId, boolean completed) {
        EngineeringChange change = loadDetailed(changeId);
        rejectIfDone(change, "Completed changes are read-only");
        if (change.getStatus() == ChangeStatus.VERIFIED) {
            throw new DomainConflictException("Criteria are locked while change is VERIFIED");
        }

        AcceptanceCriterion criterion = change.getAcceptanceCriteria().stream()
                .filter(existing -> existing.getId().equals(criterionId))
                .findFirst()
                .orElseThrow(() -> new ResourceNotFoundException("Acceptance criterion not found"));

        criterion.setCompleted(completed);
        touch(change);
        EngineeringChange saved = repository.save(change);
        initializeDetails(saved);
        return saved;
    }

    @Transactional
    public EngineeringChange transitionStatus(UUID id, ChangeStatus targetStatus) {
        EngineeringChange change = loadDetailed(id);
        ChangeStatus current = change.getStatus();

        if (!availableTransitions(change).contains(targetStatus)) {
            log.info("Rejected transition for change {} from {} to {}", change.getId(), current, targetStatus);
            throw new DomainConflictException("Invalid status transition");
        }

        change.setStatus(targetStatus);
        touch(change);
        EngineeringChange saved = repository.save(change);
        initializeDetails(saved);
        log.info("Transitioned engineering change {} from {} to {}", saved.getId(), current, targetStatus);
        return saved;
    }

    @Transactional
    public void delete(UUID id) {
        EngineeringChange change = loadDetailed(id);
        if (change.getStatus() != ChangeStatus.DRAFT) {
            throw new DomainConflictException("Only DRAFT changes can be deleted");
        }
        repository.delete(change);
    }

    public List<ChangeStatus> availableTransitions(EngineeringChange change) {
        return TRANSITIONS.getOrDefault(change.getStatus(), List.of()).stream()
                .filter(candidate -> candidate != ChangeStatus.VERIFIED || allCriteriaCompleted(change))
                .toList();
    }

    public boolean isDeletable(EngineeringChange change) {
        return change.getStatus() == ChangeStatus.DRAFT;
    }

    private EngineeringChange loadDetailed(UUID id) {
        EngineeringChange change = repository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Engineering change not found"));
        initializeDetails(change);
        return change;
    }

    private static Map<ChangeStatus, List<ChangeStatus>> buildTransitions() {
        Map<ChangeStatus, List<ChangeStatus>> transitions = new EnumMap<>(ChangeStatus.class);
        transitions.put(ChangeStatus.DRAFT, List.of(ChangeStatus.READY));
        transitions.put(ChangeStatus.READY, List.of(ChangeStatus.DRAFT, ChangeStatus.IN_PROGRESS));
        transitions.put(ChangeStatus.IN_PROGRESS, List.of(ChangeStatus.READY, ChangeStatus.VERIFIED));
        transitions.put(ChangeStatus.VERIFIED, List.of(ChangeStatus.IN_PROGRESS, ChangeStatus.DONE));
        transitions.put(ChangeStatus.DONE, List.of());
        return transitions;
    }

    private List<AcceptanceCriterion> buildCriteria(List<EngineeringChangeCriterionInput> inputs,
                                                    List<AcceptanceCriterion> existingCriteria,
                                                    boolean lockedCriteria) {
        List<EngineeringChangeCriterionInput> safeInputs = Objects.requireNonNull(inputs, "criteria are required");
        if (safeInputs.isEmpty()) {
            throw new BadRequestException("At least one acceptance criterion is required");
        }

        Map<UUID, AcceptanceCriterion> existingById = existingCriteria.stream()
                .collect(Collectors.toMap(AcceptanceCriterion::getId, criterion -> criterion));

        Set<UUID> seenIds = new LinkedHashSet<>();
        List<AcceptanceCriterion> rewritten = new ArrayList<>();
        for (EngineeringChangeCriterionInput input : safeInputs) {
            String normalizedText = normalizeText(input.text(), "criteria.text");
            UUID id = input.id();
            if (id != null) {
                if (!seenIds.add(id)) {
                    throw new BadRequestException("Duplicate acceptance criterion ids are not allowed");
                }
                AcceptanceCriterion existing = existingById.get(id);
                if (existing == null) {
                    throw new BadRequestException("Acceptance criterion id does not belong to this engineering change");
                }
                if (lockedCriteria && !existing.getText().equals(normalizedText)) {
                    throw new DomainConflictException("Criteria are locked while change is VERIFIED");
                }
                existing.setText(normalizedText);
                rewritten.add(existing);
                continue;
            }

            if (lockedCriteria) {
                throw new DomainConflictException("Criteria are locked while change is VERIFIED");
            }
            rewritten.add(new AcceptanceCriterion(UUID.randomUUID(), normalizedText, false));
        }

        if (lockedCriteria && existingCriteria.size() != rewritten.size()) {
            throw new DomainConflictException("Criteria are locked while change is VERIFIED");
        }

        if (lockedCriteria) {
            for (int index = 0; index < existingCriteria.size(); index++) {
                if (!existingCriteria.get(index).getId().equals(rewritten.get(index).getId())) {
                    throw new DomainConflictException("Criteria are locked while change is VERIFIED");
                }
            }
        }

        return rewritten;
    }

    private List<String> normalizeAffectedComponents(List<String> affectedComponents) {
        List<String> safeComponents = affectedComponents == null ? List.of() : affectedComponents;
        List<String> normalized = safeComponents.stream()
                .map(component -> normalizeText(component, "affectedComponents"))
                .toList();

        Set<String> seen = new LinkedHashSet<>();
        normalized.stream()
                .filter(Predicate.not(seen::add))
                .findFirst()
                .ifPresent(duplicate -> {
                    throw new BadRequestException("Duplicate affected component values are not allowed");
                });

        return normalized;
    }

    private String normalizeText(String input, String fieldName) {
        String normalized = Objects.requireNonNull(input, fieldName + " is required").trim();
        if (normalized.isEmpty()) {
            throw new BadRequestException(fieldName + " must not be blank");
        }
        return normalized;
    }

    private void rejectIfDone(EngineeringChange change, String message) {
        if (change.getStatus() == ChangeStatus.DONE) {
            throw new DomainConflictException(message);
        }
    }

    private boolean allCriteriaCompleted(EngineeringChange change) {
        return change.getAcceptanceCriteria().stream().allMatch(AcceptanceCriterion::isCompleted);
    }

    private void touch(EngineeringChange change) {
        change.setUpdatedAt(Instant.now(clock));
    }

    private void initializeDetails(EngineeringChange change) {
        Hibernate.initialize(change.getAcceptanceCriteria());
        Hibernate.initialize(change.getAffectedComponents());
    }
}
