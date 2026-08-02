package com.example.droolsotel.otel;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.kie.api.event.rule.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class DroolsOtelAgendaEventListener implements AgendaEventListener {

    private static final Logger log = LoggerFactory.getLogger(DroolsOtelAgendaEventListener.class);

    private final Tracer tracer;
    private final LongCounter rulesFiredCounter;
    private final Map<org.kie.api.runtime.rule.Match, Span> activeRuleSpans = new ConcurrentHashMap<>();
    private final Map<org.kie.api.runtime.rule.Match, Scope> activeScopes = new ConcurrentHashMap<>();
    private final ThreadLocal<Span> activeEvalSpan = new ThreadLocal<>();

    public DroolsOtelAgendaEventListener(Tracer tracer, Meter meter) {
        this.tracer = tracer;
        this.rulesFiredCounter = meter.counterBuilder("drools_rules_fired_total")
                .setDescription("Total number of Drools rules fired")
                .setUnit("1")
                .build();
    }

    public void startEvaluationSpan() {
        Span span = tracer.spanBuilder("drools.engine.evaluate")
                .setAttribute(AttributeKey.stringKey("drools.engine.phase"), "agenda_evaluation")
                .startSpan();
        activeEvalSpan.set(span);
    }

    public void endEvaluationSpan() {
        Span span = activeEvalSpan.get();
        if (span != null) {
            span.end();
            activeEvalSpan.remove();
        }
    }

    @Override
    public void matchCreated(MatchCreatedEvent event) {
        log.debug("Drools match created: {}", event.getMatch().getRule().getName());
    }

    @Override
    public void matchCancelled(MatchCancelledEvent event) {
        log.debug("Drools match cancelled: {}", event.getMatch().getRule().getName());
    }

    @Override
    public void beforeMatchFired(BeforeMatchFiredEvent event) {
        // End the active evaluation span when a rule starts executing
        endEvaluationSpan();

        org.kie.api.runtime.rule.Match match = event.getMatch();
        String ruleName = match.getRule().getName();
        String packageName = match.getRule().getPackageName();

        log.info("Executing Drools Rule: [Package: {}, Rule: {}]", packageName, ruleName);

        Span span = tracer.spanBuilder("drools.rule." + ruleName)
                .setAttribute(AttributeKey.stringKey("drools.rule.name"), ruleName)
                .setAttribute(AttributeKey.stringKey("drools.package.name"), packageName)
                .startSpan();

        Scope scope = span.makeCurrent();
        activeRuleSpans.put(match, span);
        activeScopes.put(match, scope);
    }

    @Override
    public void afterMatchFired(AfterMatchFiredEvent event) {
        org.kie.api.runtime.rule.Match match = event.getMatch();
        String ruleName = match.getRule().getName();

        Scope scope = activeScopes.remove(match);
        if (scope != null) {
            scope.close();
        }

        Span span = activeRuleSpans.remove(match);
        if (span != null) {
            span.setAttribute(AttributeKey.booleanKey("drools.rule.fired"), true);
            span.end();
        }

        rulesFiredCounter.add(1, Attributes.of(AttributeKey.stringKey("rule_name"), ruleName));
        log.info("Completed Drools Rule: {}", ruleName);

        // Start a new evaluation span for the next gap
        startEvaluationSpan();
    }

    @Override
    public void agendaGroupPopped(AgendaGroupPoppedEvent event) {}

    @Override
    public void agendaGroupPushed(AgendaGroupPushedEvent event) {}

    @Override
    public void beforeRuleFlowGroupActivated(RuleFlowGroupActivatedEvent event) {}

    @Override
    public void afterRuleFlowGroupActivated(RuleFlowGroupActivatedEvent event) {}

    @Override
    public void beforeRuleFlowGroupDeactivated(RuleFlowGroupDeactivatedEvent event) {}

    @Override
    public void afterRuleFlowGroupDeactivated(RuleFlowGroupDeactivatedEvent event) {}
}
