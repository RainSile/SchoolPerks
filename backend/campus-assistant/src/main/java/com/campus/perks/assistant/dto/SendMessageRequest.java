package com.campus.perks.assistant.dto;

import jakarta.validation.constraints.*;

public record SendMessageRequest(@NotBlank @Size(max = 4000) String message) {}
