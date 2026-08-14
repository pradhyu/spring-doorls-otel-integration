package com.example.droolsotel.otel;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation to mark fields or classes as safe for OpenTelemetry traces/logs.
 * Fields not annotated with this will be omitted from telemetry summaries to protect PII.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.FIELD, ElementType.TYPE})
public @interface TelemetrySafe {
}
