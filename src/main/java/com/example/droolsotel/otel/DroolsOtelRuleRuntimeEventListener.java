package com.example.droolsotel.otel;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.LongCounter;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import org.kie.api.event.rule.ObjectDeletedEvent;
import org.kie.api.event.rule.ObjectInsertedEvent;
import org.kie.api.event.rule.ObjectUpdatedEvent;
import org.kie.api.event.rule.RuleRuntimeEventListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class DroolsOtelRuleRuntimeEventListener implements RuleRuntimeEventListener {

    private static final Logger log = LoggerFactory.getLogger(DroolsOtelRuleRuntimeEventListener.class);

    private final LongCounter factsCounter;

    public DroolsOtelRuleRuntimeEventListener(Meter meter) {
        this.factsCounter = meter.counterBuilder("drools_facts_total")
                .setDescription("Total number of facts processed in Drools engine")
                .setUnit("1")
                .build();
    }

    @Override
    public void objectInserted(ObjectInsertedEvent event) {
        Object object = event.getObject();
        String className = object.getClass().getSimpleName();
        log.debug("Drools Fact Inserted: {}", className);

        factsCounter.add(1, Attributes.of(
                AttributeKey.stringKey("fact_type"), className,
                AttributeKey.stringKey("operation"), "INSERT"
        ));

        Span currentSpan = Span.current();
        if (currentSpan.getSpanContext().isValid()) {
            currentSpan.addEvent("fact_inserted", Attributes.of(
                    AttributeKey.stringKey("fact.class"), className,
                    AttributeKey.stringKey("fact.hash"), String.valueOf(System.identityHashCode(object))
            ));
        }
    }

    @Override
    public void objectUpdated(ObjectUpdatedEvent event) {
        Object object = event.getObject();
        String className = object.getClass().getSimpleName();
        log.debug("Drools Fact Updated: {}", className);

        factsCounter.add(1, Attributes.of(
                AttributeKey.stringKey("fact_type"), className,
                AttributeKey.stringKey("operation"), "UPDATE"
        ));

        Span currentSpan = Span.current();
        if (currentSpan.getSpanContext().isValid()) {
            currentSpan.addEvent("fact_updated", Attributes.of(
                    AttributeKey.stringKey("fact.class"), className,
                    AttributeKey.stringKey("fact.hash"), String.valueOf(System.identityHashCode(object))
            ));
        }
    }

    @Override
    public void objectDeleted(ObjectDeletedEvent event) {
        Object object = event.getOldObject();
        String className = object != null ? object.getClass().getSimpleName() : "Unknown";
        log.debug("Drools Fact Retracted: {}", className);

        factsCounter.add(1, Attributes.of(
                AttributeKey.stringKey("fact_type"), className,
                AttributeKey.stringKey("operation"), "DELETE"
        ));

        Span currentSpan = Span.current();
        if (currentSpan.getSpanContext().isValid()) {
            currentSpan.addEvent("fact_deleted", Attributes.of(
                    AttributeKey.stringKey("fact.class"), className,
                    AttributeKey.stringKey("fact.hash"), String.valueOf(System.identityHashCode(object))
            ));
        }
    }
}
