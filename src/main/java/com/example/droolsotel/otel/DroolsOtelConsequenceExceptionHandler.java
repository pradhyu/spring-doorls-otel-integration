package com.example.droolsotel.otel;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.StatusCode;
import org.kie.api.runtime.rule.ConsequenceException;
import org.kie.api.runtime.rule.ConsequenceExceptionHandler;
import org.kie.api.runtime.rule.Match;
import org.kie.api.runtime.rule.RuleRuntime;

/**
 * Custom consequence exception handler that records rule consequence execution failures in OpenTelemetry spans.
 */
public class DroolsOtelConsequenceExceptionHandler implements ConsequenceExceptionHandler {

    @Override
    public void handleException(Match match, RuleRuntime ruleRuntime, Exception exception) {
        Span currentSpan = Span.current();
        if (currentSpan.getSpanContext().isValid()) {
            currentSpan.setStatus(StatusCode.ERROR, "Exception in consequence of rule: " + match.getRule().getName());
            currentSpan.recordException(exception);
        }
        // Rethrow the exception as runtime exception to propagate failure
        if (exception instanceof RuntimeException) {
            throw (RuntimeException) exception;
        } else {
            throw new RuntimeException(exception);
        }
    }
}
