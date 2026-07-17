package com.changepilot.change.service;

import com.changepilot.change.domain.AcceptanceCriterion;
import com.changepilot.change.domain.ChangeStatus;
import com.changepilot.change.domain.EngineeringChange;
import com.changepilot.change.domain.RiskLevel;
import com.changepilot.change.persistence.EngineeringChangeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EngineeringChangeServiceTest {

    private final EngineeringChangeRepository repository = mock(EngineeringChangeRepository.class);
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-17T00:00:00Z"), ZoneOffset.UTC);

    private EngineeringChangeService service;

    @BeforeEach
    void setUp() {
        service = new EngineeringChangeService(repository, clock);
    }

    @Test
    void createInitializesDraftAndNormalizesValues() {
        when(repository.save(any(EngineeringChange.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EngineeringChange created = service.create(new EngineeringChangeUpsertCommand(
                "  Network refresh  ",
                "  Update network rules  ",
                RiskLevel.HIGH,
                List.of(" api ", "worker"),
                List.of(new EngineeringChangeCriterionInput(null, "  Validate alerts  "))));

        assertThat(created.getStatus()).isEqualTo(ChangeStatus.DRAFT);
        assertThat(created.getTitle()).isEqualTo("Network refresh");
        assertThat(created.getAffectedComponents()).containsExactly("api", "worker");
        assertThat(created.getAcceptanceCriteria()).hasSize(1);
        assertThat(created.getAcceptanceCriteria().getFirst().isCompleted()).isFalse();
    }

    @Test
    void updatePreservesExistingCriterionCompletionAndAddsNewCriterion() {
        EngineeringChange existing = change(ChangeStatus.IN_PROGRESS,
                criterion(UUID.randomUUID(), "Old text", true),
                criterion(UUID.randomUUID(), "Remove me", false));
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(repository.save(any(EngineeringChange.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EngineeringChange updated = service.update(existing.getId(), new EngineeringChangeUpsertCommand(
                "Updated title",
                "Updated description",
                RiskLevel.MEDIUM,
                List.of("api"),
                List.of(
                        new EngineeringChangeCriterionInput(existing.getAcceptanceCriteria().get(0).getId(), "New text"),
                        new EngineeringChangeCriterionInput(null, "Brand new")
                )));

        assertThat(updated.getAcceptanceCriteria()).hasSize(2);
        assertThat(updated.getAcceptanceCriteria().get(0).getText()).isEqualTo("New text");
        assertThat(updated.getAcceptanceCriteria().get(0).isCompleted()).isTrue();
        assertThat(updated.getAcceptanceCriteria().get(1).getText()).isEqualTo("Brand new");
        assertThat(updated.getAcceptanceCriteria().get(1).isCompleted()).isFalse();
    }

    @Test
    void updateRejectsCriterionChangesWhileVerified() {
        EngineeringChange existing = change(ChangeStatus.VERIFIED,
                criterion(UUID.randomUUID(), "Locked", true));
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(existing.getId(), new EngineeringChangeUpsertCommand(
                "Title",
                "Description",
                RiskLevel.LOW,
                List.of(),
                List.of(new EngineeringChangeCriterionInput(existing.getAcceptanceCriteria().getFirst().getId(), "Changed")))))
                .isInstanceOf(DomainConflictException.class)
                .hasMessageContaining("locked");
    }

    @Test
    void updateRejectsDoneChange() {
        EngineeringChange existing = change(ChangeStatus.DONE, criterion(UUID.randomUUID(), "Locked", true));
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.update(existing.getId(), commandWithCriterion(null, "Still locked")))
                .isInstanceOf(DomainConflictException.class)
                .hasMessageContaining("read-only");
    }

    @Test
    void updateCriterionCompletionTogglesValue() {
        AcceptanceCriterion criterion = criterion(UUID.randomUUID(), "Check", false);
        EngineeringChange existing = change(ChangeStatus.READY, criterion);
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(repository.save(any(EngineeringChange.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EngineeringChange updated = service.updateCriterionCompletion(existing.getId(), criterion.getId(), true);

        assertThat(updated.getAcceptanceCriteria().getFirst().isCompleted()).isTrue();
    }

    @ParameterizedTest
    @MethodSource("validTransitions")
    void allowsAllConfiguredTransitions(ChangeStatus start, ChangeStatus target) {
        EngineeringChange existing = change(start, criterion(UUID.randomUUID(), "Check", true));
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));
        when(repository.save(any(EngineeringChange.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EngineeringChange updated = service.transitionStatus(existing.getId(), target);

        assertThat(updated.getStatus()).isEqualTo(target);
    }

    @ParameterizedTest
    @MethodSource("invalidTransitions")
    void rejectsRepresentativeInvalidTransitions(ChangeStatus start, ChangeStatus target) {
        EngineeringChange existing = change(start, criterion(UUID.randomUUID(), "Check", true));
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.transitionStatus(existing.getId(), target))
                .isInstanceOf(DomainConflictException.class)
                .hasMessageContaining("Invalid status transition");
    }

    @Test
    void verifiedTransitionRequiresAllCriteriaCompleted() {
        EngineeringChange existing = change(ChangeStatus.IN_PROGRESS,
                criterion(UUID.randomUUID(), "Done", true),
                criterion(UUID.randomUUID(), "Pending", false));
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.transitionStatus(existing.getId(), ChangeStatus.VERIFIED))
                .isInstanceOf(DomainConflictException.class);
    }

    @Test
    void deleteAllowsDraftOnly() {
        EngineeringChange draft = change(ChangeStatus.DRAFT, criterion(UUID.randomUUID(), "Draft", false));
        when(repository.findById(draft.getId())).thenReturn(Optional.of(draft));

        service.delete(draft.getId());

        verify(repository).delete(draft);
    }

    @Test
    void deleteRejectsNonDraft() {
        EngineeringChange existing = change(ChangeStatus.READY, criterion(UUID.randomUUID(), "Ready", false));
        when(repository.findById(existing.getId())).thenReturn(Optional.of(existing));

        assertThatThrownBy(() -> service.delete(existing.getId()))
                .isInstanceOf(DomainConflictException.class)
                .hasMessageContaining("DRAFT");
    }

    @Test
    void rejectsDuplicateAffectedComponentsAfterNormalization() {
        assertThatThrownBy(() -> service.create(new EngineeringChangeUpsertCommand(
                "Title",
                "Description",
                RiskLevel.LOW,
                List.of(" api ", "api"),
                List.of(new EngineeringChangeCriterionInput(null, "Criterion")))))
                .isInstanceOf(BadRequestException.class)
                .hasMessageContaining("Duplicate affected component");
    }

    @Test
    void createSavesTouchedTimestamps() {
        when(repository.save(any(EngineeringChange.class))).thenAnswer(invocation -> invocation.getArgument(0));

        service.create(commandWithCriterion(null, "Criterion"));

        ArgumentCaptor<EngineeringChange> captor = ArgumentCaptor.forClass(EngineeringChange.class);
        verify(repository).save(captor.capture());
        assertThat(captor.getValue().getCreatedAt()).isEqualTo(Instant.parse("2026-07-17T00:00:00Z"));
        assertThat(captor.getValue().getUpdatedAt()).isEqualTo(Instant.parse("2026-07-17T00:00:00Z"));
    }

    private EngineeringChangeUpsertCommand commandWithCriterion(UUID id, String text) {
        return new EngineeringChangeUpsertCommand("Title", "Description", RiskLevel.LOW, List.of(), List.of(new EngineeringChangeCriterionInput(id, text)));
    }

    private EngineeringChange change(ChangeStatus status, AcceptanceCriterion... criteria) {
        EngineeringChange change = new EngineeringChange(UUID.randomUUID(), "Title", "Description", RiskLevel.LOW,
                status, Instant.parse("2026-07-16T00:00:00Z"), Instant.parse("2026-07-16T00:00:00Z"));
        change.replaceAcceptanceCriteria(List.of(criteria));
        return change;
    }

    private AcceptanceCriterion criterion(UUID id, String text, boolean completed) {
        return new AcceptanceCriterion(id, text, completed);
    }

    private static Stream<Arguments> validTransitions() {
        return Stream.of(
                Arguments.of(ChangeStatus.DRAFT, ChangeStatus.READY),
                Arguments.of(ChangeStatus.READY, ChangeStatus.DRAFT),
                Arguments.of(ChangeStatus.READY, ChangeStatus.IN_PROGRESS),
                Arguments.of(ChangeStatus.IN_PROGRESS, ChangeStatus.READY),
                Arguments.of(ChangeStatus.IN_PROGRESS, ChangeStatus.VERIFIED),
                Arguments.of(ChangeStatus.VERIFIED, ChangeStatus.IN_PROGRESS),
                Arguments.of(ChangeStatus.VERIFIED, ChangeStatus.DONE)
        );
    }

    private static Stream<Arguments> invalidTransitions() {
        return Stream.of(
                Arguments.of(ChangeStatus.DRAFT, ChangeStatus.DONE),
                Arguments.of(ChangeStatus.READY, ChangeStatus.VERIFIED),
                Arguments.of(ChangeStatus.DONE, ChangeStatus.VERIFIED)
        );
    }
}
