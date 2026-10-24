package com.campus.perks.community.serviceImpl;

import com.campus.perks.community.service.PostService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Service;

@Service
@ConditionalOnProperty(name = "campus.auth.enabled", havingValue = "false", matchIfMissing = true)
public class DisabledPostService implements PostService {}
