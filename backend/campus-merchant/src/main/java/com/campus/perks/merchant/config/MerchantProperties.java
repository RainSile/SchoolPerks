package com.campus.perks.merchant.config;

import jakarta.validation.constraints.*;
import java.util.Set;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "campus.merchant")
public record MerchantProperties(
    @NotEmpty Set<@Pattern(regexp = "[A-Z_]{1,64}") String> categories, @Min(1) int maxMembers) {}
