package com.campus.perks.user.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({AuthProperties.class, PasswordProperties.class, AvatarProperties.class, CampusEvidenceProperties.class})
public class UserConfiguration {}
