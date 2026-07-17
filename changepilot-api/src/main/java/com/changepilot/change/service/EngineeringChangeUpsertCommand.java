package com.changepilot.change.service;

import com.changepilot.change.domain.RiskLevel;

import java.util.List;

public record EngineeringChangeUpsertCommand(
        String title,
        String description,
        RiskLevel risk,
        List<String> affectedComponents,
        List<EngineeringChangeCriterionInput> criteria
) {
}
