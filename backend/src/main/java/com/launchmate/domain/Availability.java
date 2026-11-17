package com.launchmate.domain;

/** UNKNOWN means a check failed or was not definitive. It is never reported as UNAVAILABLE. */
public enum Availability {
    AVAILABLE, UNAVAILABLE, UNKNOWN, NOT_CHECKED
}
