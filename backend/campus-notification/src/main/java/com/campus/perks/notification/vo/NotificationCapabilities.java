package com.campus.perks.notification.vo;

public record NotificationCapabilities(
    boolean enabled, String streamPath, int pingIntervalSeconds, int reconnectDelaySeconds) {}
