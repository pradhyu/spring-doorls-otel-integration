package com.example.droolsotel.otel;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;

import org.kie.api.event.rule.ObjectDeletedEvent;
import org.kie.api.event.rule.ObjectInsertedEvent;
import org.kie.api.event.rule.ObjectUpdatedEvent;
import org.kie.api.event.rule.RuleRuntimeEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DroolsOtelRuleRuntimeEventListener implements RuleRuntimeEventListener {

    private static final Logger log = LoggerFactory.getLogger(DroolsOtelRuleRuntimeEventListener.class);

    private final Tracer tracer;
    private final LongCounter factsCounter;

    public DroolsOtelRuleRuntimeEventListener(Tracer tracer, Meter meter) {
        this.tracer = tracer;
        this.factsCounter = meter.counterBuilder("drools_facts_total")
                .setDescription("Total number of facts processed in Drools engine")
                .setUnit("1")
                .build();
    }

    @Override
    public void objectInserted(ObjectInsertedEvent event) {
        Object object = event.getObject();
        String className = object.getClass().getSimpleName();
        log.info("Drools Fact Inserted: {} -> {}", className, object);

        factsCounter.add(1, Attributes.of(
                AttributeKey.stringKey("fact_type"), className,
                AttributeKey.stringKey("operation"), "INSERT"
        ));

        Span span = tracer.spanBuilder("drools.fact.insert")
                .setAttribute(AttributeKey.stringKey("fact.class"), className)
                .setAttribute(AttributeKey.stringKey("fact.value"), object.toString())
                .startSpan();
        try {
            span.addEvent("fact_inserted_started");
        } finally {
            span.end();
        }
    }

    @Override
    public void objectUpdated(ObjectUpdatedEvent event) {
        Object object = event.getObject();
        String className = object.getClass().getSimpleName();
        log.info("Drools Fact Updated: {} -> {}", className, object);

        factsCounter.add(1, Attributes.of(
                AttributeKey.stringKey("fact_type"), className,
                AttributeKey.stringKey("operation"), "UPDATE"
        ));

        Span span = tracer.spanBuilder("drools.fact.update")
                .setAttribute(AttributeKey.stringKey("fact.class"), className)
                .setAttribute(AttributeKey.stringKey("fact.old_value"), event.getOldObject() != null ? event.getOldObject().toString() : "null")
                .setAttribute(AttributeKey.stringKey("fact.new_value"), object.toString())
                .startSpan();
        try {
            span.addEvent("edit_started");
        } finally {
            span.end();
        }
    }

    @Override
    public void objectDeleted(ObjectDeletedEvent event) {
        Object object = event.getOldObject();
        String className = object != null ? object.getClass().getSimpleName() : "Unknown";
        log.info("Drools Fact Retracted: {}", className);

        factsCounter.add(1, Attributes.of(
                AttributeKey.stringKey("fact_type"), className,
                AttributeKey.stringKey("operation"), "DELETE"
        ));

        Span span = tracer.spanBuilder("drools.fact.delete")
                .setAttribute(AttributeKey.stringKey("fact.class"), className)
                .setAttribute(AttributeKey.stringKey("fact.old_value"), object != null ? object.toString() : "null")
                .startSpan();
        span.end();
    }
}
