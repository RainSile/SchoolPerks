package com.campus.perks.merchant.serviceImpl;

import com.campus.perks.merchant.service.LocationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledLocationService implements LocationService {}
