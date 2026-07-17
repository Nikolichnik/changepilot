package com.changepilot.change.api;

import com.changepilot.change.domain.ChangeStatus;
import com.changepilot.change.domain.RiskLevel;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record EngineeringChangeDetailResponse(
        UUID id,
        String title,
        String description,
        RiskLevel risk,
        ChangeStatus status,
        List<String> affectedComponents,
        List<AcceptanceCriterionResponse> criteria,
        int completedCriteriaCount,
        int totalCriteriaCount,
        List<ChangeStatus> availableTransitions,
        boolean deletable,
        Instant createdAt,
        Instant updatedAt
) {
}
