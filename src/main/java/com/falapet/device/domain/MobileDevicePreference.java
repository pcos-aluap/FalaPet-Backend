package com.falapet.device.domain;

/** Persisted preference only; playback remains a local mobile decision. */
public record MobileDevicePreference(boolean localPlaybackEnabled, long version) {
}
