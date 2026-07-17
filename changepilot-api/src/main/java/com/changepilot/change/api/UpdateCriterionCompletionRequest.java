package com.changepilot.change.api;

import jakarta.validation.constraints.NotNull;

public record UpdateCriterionCompletionRequest(
        @NotNull(message = "completed is required")
        Boolean completed
) {
}
