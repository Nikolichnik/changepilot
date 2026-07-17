package com.changepilot.change.api;

import java.util.UUID;

public record AcceptanceCriterionResponse(
        UUID id,
        String text,
        boolean completed
) {
}
