package com.launchmate.common;

import java.time.Instant;

/** The error envelope from LLD section 6.8. */
public record ApiError(Instant timestamp, int status, String code, String message, String traceId) {}
