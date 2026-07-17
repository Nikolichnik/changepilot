package com.changepilot.change.api;

import com.changepilot.change.domain.ChangeStatus;
import jakarta.validation.constraints.NotNull;

public record UpdateChangeStatusRequest(
        @NotNull(message = "targetStatus is required")
        ChangeStatus targetStatus
) {
}
