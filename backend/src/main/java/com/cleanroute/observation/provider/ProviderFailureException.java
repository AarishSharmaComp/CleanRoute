package com.cleanroute.observation.provider;

import java.time.Duration;

/** Typed, provider-neutral failure information; exception messages must not contain credentials. */
public class ProviderFailureException extends RuntimeException {
    public enum Type { TIMEOUT, TEMPORARY_FAILURE, RATE_LIMITED }
    private final Type type;
    private final Duration retryAfter;

    public ProviderFailureException(Type type) { this(type, null); }
    public ProviderFailureException(Type type, Duration retryAfter) {
        super(type.name());
        this.type = type;
        this.retryAfter = retryAfter;
    }
    public Type getType() { return type; }
    public Duration getRetryAfter() { return retryAfter; }
    public static ProviderFailureException timeout() { return new ProviderFailureException(Type.TIMEOUT); }
    public static ProviderFailureException temporary() { return new ProviderFailureException(Type.TEMPORARY_FAILURE); }
    public static ProviderFailureException rateLimited(Duration retryAfter) { return new ProviderFailureException(Type.RATE_LIMITED, retryAfter); }
}
