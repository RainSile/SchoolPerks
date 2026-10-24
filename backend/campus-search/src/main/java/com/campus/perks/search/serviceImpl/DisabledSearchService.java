package com.campus.perks.search.serviceImpl;

import com.campus.perks.search.service.SearchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledSearchService implements SearchService {}
