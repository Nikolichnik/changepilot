package com.changepilot.change.api;

import com.changepilot.change.domain.AcceptanceCriterion;
import com.changepilot.change.domain.ChangeStatus;
import com.changepilot.change.domain.EngineeringChange;
import com.changepilot.change.service.EngineeringChangeCriterionInput;
import com.changepilot.change.service.EngineeringChangeService;
import com.changepilot.change.service.EngineeringChangeUpsertCommand;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/engineering-changes")
public class EngineeringChangeController {

    private final EngineeringChangeService service;

    public EngineeringChangeController(EngineeringChangeService service) {
        this.service = service;
    }

    @PostMapping
    @Operation(summary = "Create an engineering change", responses = {
            @ApiResponse(responseCode = "201", description = "Engineering change created"),
            @ApiResponse(responseCode = "400", description = "Request validation failed")
    })
    public ResponseEntity<EngineeringChangeDetailResponse> create(@Valid @RequestBody EngineeringChangeUpsertRequest request) {
        EngineeringChange created = service.create(toCommand(request));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(created.getId()).toUri();
        return ResponseEntity.created(location).body(toDetailResponse(created));
    }

    @GetMapping
    public List<EngineeringChangeSummaryResponse> list(@RequestParam(required = false) ChangeStatus status) {
        return service.list(status).stream().map(this::toSummaryResponse).toList();
    }

    @GetMapping("/{id}")
    public EngineeringChangeDetailResponse get(@PathVariable UUID id) {
        return toDetailResponse(service.get(id));
    }

    @PutMapping("/{id}")
    public EngineeringChangeDetailResponse update(@PathVariable UUID id,
                                                  @Valid @RequestBody EngineeringChangeUpsertRequest request) {
        return toDetailResponse(service.update(id, toCommand(request)));
    }

    @PatchMapping("/{id}/criteria/{criterionId}")
    public EngineeringChangeDetailResponse updateCriterionCompletion(@PathVariable UUID id,
                                                                     @PathVariable UUID criterionId,
                                                                     @Valid @RequestBody UpdateCriterionCompletionRequest request) {
        return toDetailResponse(service.updateCriterionCompletion(id, criterionId, request.completed()));
    }

    @PatchMapping("/{id}/status")
    public EngineeringChangeDetailResponse updateStatus(@PathVariable UUID id,
                                                        @Valid @RequestBody UpdateChangeStatusRequest request) {
        return toDetailResponse(service.transitionStatus(id, request.targetStatus()));
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Delete a draft engineering change", responses = {
            @ApiResponse(responseCode = "204", description = "Engineering change deleted"),
            @ApiResponse(responseCode = "404", description = "Engineering change not found"),
            @ApiResponse(responseCode = "409", description = "Engineering change is not a draft")
    })
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        service.delete(id);
        return ResponseEntity.noContent().build();
    }

    private EngineeringChangeUpsertCommand toCommand(EngineeringChangeUpsertRequest request) {
        return new EngineeringChangeUpsertCommand(
                request.title(),
                request.description(),
                request.risk(),
                request.affectedComponents(),
                request.criteria().stream()
                        .map(criterion -> new EngineeringChangeCriterionInput(criterion.id(), criterion.text()))
                        .toList());
    }

    private EngineeringChangeSummaryResponse toSummaryResponse(EngineeringChange change) {
        int total = change.getAcceptanceCriteria().size();
        int completed = completedCount(change);
        return new EngineeringChangeSummaryResponse(
                change.getId(),
                change.getTitle(),
                change.getRisk(),
                change.getStatus(),
                completed,
                total,
                change.getUpdatedAt());
    }

    private EngineeringChangeDetailResponse toDetailResponse(EngineeringChange change) {
        int total = change.getAcceptanceCriteria().size();
        int completed = completedCount(change);
        return new EngineeringChangeDetailResponse(
                change.getId(),
                change.getTitle(),
                change.getDescription(),
                change.getRisk(),
                change.getStatus(),
                List.copyOf(change.getAffectedComponents()),
                change.getAcceptanceCriteria().stream()
                        .map(this::toCriterionResponse)
                        .toList(),
                completed,
                total,
                service.availableTransitions(change),
                service.isDeletable(change),
                change.getCreatedAt(),
                change.getUpdatedAt());
    }

    private AcceptanceCriterionResponse toCriterionResponse(AcceptanceCriterion criterion) {
        return new AcceptanceCriterionResponse(criterion.getId(), criterion.getText(), criterion.isCompleted());
    }

    private int completedCount(EngineeringChange change) {
        return (int) change.getAcceptanceCriteria().stream().filter(AcceptanceCriterion::isCompleted).count();
    }
}
