package com.changepilot.change.api;

import com.changepilot.change.domain.DomainConstraints;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record EngineeringChangeCriterionRequest(
        UUID id,
        @NotBlank(message = "Criterion text is required")
        @Size(max = DomainConstraints.CRITERION_TEXT_MAX_LENGTH, message = "Criterion text is too long")
        String text
) {
}
