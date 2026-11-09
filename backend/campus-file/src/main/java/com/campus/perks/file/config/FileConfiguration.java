package com.campus.perks.file.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({FileProperties.class, ImageStorageProperties.class})
public class FileConfiguration {}
