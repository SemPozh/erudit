package com.erudit.content.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record ContentReportRequest(
        @NotBlank
        @Size(min = 10, max = 500)
        String reason) {
}
