package com.changepilot.change.api;

import com.changepilot.change.domain.ChangeStatus;
import com.changepilot.change.domain.RiskLevel;

import java.time.Instant;
import java.util.UUID;

public record EngineeringChangeSummaryResponse(
        UUID id,
        String title,
        RiskLevel risk,
        ChangeStatus status,
        int completedCriteriaCount,
        int totalCriteriaCount,
        Instant updatedAt
) {
}
