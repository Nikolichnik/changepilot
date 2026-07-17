package com.changepilot.change.api;

import com.changepilot.change.domain.DomainConstraints;
import com.changepilot.change.domain.RiskLevel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;

public record EngineeringChangeUpsertRequest(
        @NotBlank(message = "Title is required")
        @Size(max = DomainConstraints.TITLE_MAX_LENGTH, message = "Title is too long")
        String title,

        @NotBlank(message = "Description is required")
        @Size(max = DomainConstraints.DESCRIPTION_MAX_LENGTH, message = "Description is too long")
        String description,

        @NotNull(message = "Risk is required")
        RiskLevel risk,

        List<@NotBlank(message = "Affected component must not be blank")
             @Size(max = DomainConstraints.COMPONENT_MAX_LENGTH, message = "Affected component is too long") String> affectedComponents,

        @NotEmpty(message = "At least one acceptance criterion is required")
        List<@Valid EngineeringChangeCriterionRequest> criteria
) {
}
