package com.example.droolsotel.otel;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.kie.api.event.rule.*;
import org.kie.api.runtime.rule.Match;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Field;
import java.util.List;
import java.util.Map;
import java.util.Stack;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class DroolsOtelAgendaEventListener implements AgendaEventListener {

    private static final Logger log = LoggerFactory.getLogger(DroolsOtelAgendaEventListener.class);

    private final Tracer tracer;
    private final LongCounter rulesFiredCounter;
    private final LongCounter matchesCreatedCounter;
    private final LongCounter matchesCancelledCounter;

    private final Map<Match, Span> activeRuleSpans = new ConcurrentHashMap<>();
    private final Map<Match, Scope> activeScopes = new ConcurrentHashMap<>();
    private final ThreadLocal<Span> activeEvalSpan = new ThreadLocal<>();

    // Stack to track active agenda groups per thread
    private final ThreadLocal<Stack<String>> activeAgendaGroups = ThreadLocal.withInitial(() -> {
        Stack<String> stack = new Stack<>();
        stack.push("MAIN");
        return stack;
    });

    public DroolsOtelAgendaEventListener(Tracer tracer, Meter meter) {
        this.tracer = tracer;
        this.rulesFiredCounter = meter.counterBuilder("drools_rules_fired_total")
                .setDescription("Total number of Drools rules fired")
                .setUnit("1")
                .build();
        this.matchesCreatedCounter = meter.counterBuilder("drools_matches_created_total")
                .setDescription("Total number of Drools rule matches created")
                .setUnit("1")
                .build();
        this.matchesCancelledCounter = meter.counterBuilder("drools_matches_cancelled_total")
                .setDescription("Total number of Drools rule matches cancelled")
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
        String ruleName = event.getMatch().getRule().getName();
        log.debug("Drools match created: {}", ruleName);
        matchesCreatedCounter.add(1, Attributes.of(AttributeKey.stringKey("rule_name"), ruleName));
    }

    @Override
    public void matchCancelled(MatchCancelledEvent event) {
        String ruleName = event.getMatch().getRule().getName();
        log.debug("Drools match cancelled: {}", ruleName);
        matchesCancelledCounter.add(1, Attributes.of(AttributeKey.stringKey("rule_name"), ruleName));
    }

    @Override
    public void beforeMatchFired(BeforeMatchFiredEvent event) {
        // End the active evaluation span when a rule starts executing
        endEvaluationSpan();

        Match match = event.getMatch();
        String ruleName = match.getRule().getName();
        String packageName = match.getRule().getPackageName();

        log.info("Executing Drools Rule: [Package: {}, Rule: {}]", packageName, ruleName);

        String currentGroup = activeAgendaGroups.get().peek();

        // Extract matched facts safely
        List<String> matchedFactTypes = match.getObjects().stream()
                .map(obj -> obj.getClass().getSimpleName())
                .collect(Collectors.toList());

        Span span = tracer.spanBuilder("drools.rule." + ruleName)
                .setAttribute(AttributeKey.stringKey("drools.rule.name"), ruleName)
                .setAttribute(AttributeKey.stringKey("drools.package.name"), packageName)
                .setAttribute(AttributeKey.stringKey("drools.agenda_group"), currentGroup)
                .setAttribute(AttributeKey.stringArrayKey("drools.matched_facts"), matchedFactTypes)
                .startSpan();

        // Add telemetry-safe summary of each matching fact as a span event
        for (Object obj : match.getObjects()) {
            span.addEvent("matched_fact_details", Attributes.of(
                    AttributeKey.stringKey("fact.class"), obj.getClass().getSimpleName(),
                    AttributeKey.stringKey("fact.summary"), getSafeSummary(obj)
            ));
        }

        Scope scope = span.makeCurrent();
        activeRuleSpans.put(match, span);
        activeScopes.put(match, scope);
    }

    @Override
    public void afterMatchFired(AfterMatchFiredEvent event) {
        Match match = event.getMatch();
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
    public void agendaGroupPushed(AgendaGroupPushedEvent event) {
        String groupName = event.getAgendaGroup().getName();
        activeAgendaGroups.get().push(groupName);
        log.debug("Agenda group pushed: {}", groupName);
    }

    @Override
    public void agendaGroupPopped(AgendaGroupPoppedEvent event) {
        Stack<String> stack = activeAgendaGroups.get();
        if (stack.size() > 1) { // Retain the "MAIN" base group
            String popped = stack.pop();
            log.debug("Agenda group popped: {}", popped);
        }
    }

    private String getSafeSummary(Object obj) {
        if (obj == null) return "null";
        Class<?> clazz = obj.getClass();

        // If the class is fully marked safe, use toString
        if (clazz.isAnnotationPresent(TelemetrySafe.class)) {
            return obj.toString();
        }

        // If it's a dynamic Drools declared type (e.g. Coupon, LoyaltyPoint) in rules package,
        // it doesn't contain customer PII, only rule state, so it's safe to print fully.
        if (clazz.getPackageName().startsWith("com.example.droolsotel.rules")) {
            return obj.toString();
        }

        // Otherwise, inspect fields and serialize only those with @TelemetrySafe
        StringBuilder sb = new StringBuilder(clazz.getSimpleName()).append("{");
        boolean first = true;
        for (Field field : clazz.getDeclaredFields()) {
            if (field.isAnnotationPresent(TelemetrySafe.class)) {
                try {
                    field.setAccessible(true);
                    Object val = field.get(obj);
                    if (!first) {
                        sb.append(", ");
                    }
                    sb.append(field.getName()).append("=").append(val);
                    first = false;
                } catch (Exception e) {
                    // Ignore reflection errors
                }
            }
        }
        sb.append("}");
        return sb.toString();
    }

    @Override
    public void beforeRuleFlowGroupActivated(RuleFlowGroupActivatedEvent event) {}

    @Override
    public void afterRuleFlowGroupActivated(RuleFlowGroupActivatedEvent event) {}

    @Override
    public void beforeRuleFlowGroupDeactivated(RuleFlowGroupDeactivatedEvent event) {}

    @Override
    public void afterRuleFlowGroupDeactivated(RuleFlowGroupDeactivatedEvent event) {}
}
