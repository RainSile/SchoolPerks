package com.campus.perks.search.entity;

public record SearchSource(
    String type, String id, String schoolId, String merchantId, String title, String body) {}
