package com.example.droolsotel.service;

import com.example.droolsotel.model.CustomerFact;
import com.example.droolsotel.model.RuleExecutionRequest;
import com.example.droolsotel.model.RuleExecutionResponse;
import com.example.droolsotel.otel.DroolsOtelAgendaEventListener;
import com.example.droolsotel.otel.DroolsOtelRuleRuntimeEventListener;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.metrics.Meter;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import org.kie.api.KieServices;
import org.kie.api.builder.KieBuilder;
import org.kie.api.builder.KieFileSystem;
import org.kie.api.builder.KieModule;
import org.kie.api.runtime.KieContainer;
import org.kie.api.runtime.KieSession;
import org.kie.internal.io.ResourceFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;

@Service
public class DroolsExecutionService {

    private static final Logger log = LoggerFactory.getLogger(DroolsExecutionService.class);

    private final KieServices kieServices;
    private final KieContainer kieContainer;
    private final Tracer tracer;
    private final Meter meter;
    private final CustomerDbService dbService;

    public DroolsExecutionService(KieServices kieServices, KieContainer kieContainer,
                                  Tracer tracer, Meter meter, CustomerDbService dbService) {
        this.kieServices = kieServices;
        this.kieContainer = kieContainer;
        this.tracer = tracer;
        this.meter = meter;
        this.dbService = dbService;
    }

    public RuleExecutionResponse executeRules(RuleExecutionRequest request) {
        Span span = tracer.spanBuilder("drools.execution.process")
                .setAttribute(AttributeKey.stringKey("drools.execution.type"), 
                        request.getCustomDrl() != null ? "CUSTOM_DRL" : "DEFAULT_RULES")
                .startSpan();

        long startTime = System.currentTimeMillis();
        CustomerFact customer = request.getCustomer();
        if (customer == null) {
            customer = new CustomerFact("Anonymous", 0, "REGULAR", 0.0);
        }

        try (Scope scope = span.makeCurrent()) {
            log.info("Starting Drools rule execution for customer: {}", customer.getName());

            KieContainer activeContainer = this.kieContainer;

            // Handle custom DRL if passed in request
            if (request.getCustomDrl() != null && !request.getCustomDrl().isBlank()) {
                activeContainer = createCustomKieContainer(request.getCustomDrl());
            }

            KieSession kieSession = activeContainer.newKieSession();

            // Set Database Service as a Global Variable
            kieSession.setGlobal("dbService", dbService);

            // Register OpenTelemetry Event Listeners
            kieSession.addEventListener(new DroolsOtelAgendaEventListener(tracer, meter));
            kieSession.addEventListener(new DroolsOtelRuleRuntimeEventListener(tracer, meter));

            try {
                kieSession.insert(customer);
                
                Span fireRulesSpan = tracer.spanBuilder("drools.fireAllRules").startSpan();
                int firedCount;
                try (Scope fireScope = fireRulesSpan.makeCurrent()) {
                    firedCount = kieSession.fireAllRules();
                    fireRulesSpan.setAttribute(AttributeKey.longKey("drools.rules_fired_count"), (long) firedCount);
                } finally {
                    fireRulesSpan.end();
                }

                long executionTime = System.currentTimeMillis() - startTime;
                String traceId = span.getSpanContext().getTraceId();
                String spanId = span.getSpanContext().getSpanId();

                log.info("Drools rule execution completed. Rules Fired: {}, Time: {}ms, TraceID: {}", 
                        firedCount, executionTime, traceId);

                span.setAttribute(AttributeKey.longKey("drools.total_rules_fired"), (long) firedCount);
                span.setAttribute(AttributeKey.stringKey("drools.customer.name"), customer.getName());
                span.setAttribute(AttributeKey.doubleKey("drools.customer.final_amount"), customer.getFinalAmount());

                return new RuleExecutionResponse(
                        "SUCCESS",
                        customer,
                        firedCount,
                        new ArrayList<>(customer.getAppliedRules()),
                        executionTime,
                        traceId,
                        spanId
                );

            } finally {
                kieSession.dispose();
            }

        } catch (Exception e) {
            log.error("Error executing Drools rules: {}", e.getMessage(), e);
            span.recordException(e);
            span.setAttribute(AttributeKey.stringKey("error.type"), e.getClass().getName());
            throw e;
        } finally {
            span.end();
        }
    }

    private KieContainer createCustomKieContainer(String customDrl) {
        log.info("Compiling custom DRL provided in request...");
        KieFileSystem kfs = kieServices.newKieFileSystem();
        kfs.write("src/main/resources/rules/custom.drl", 
                ResourceFactory.newByteArrayResource(customDrl.getBytes(StandardCharsets.UTF_8)));

        KieBuilder kieBuilder = kieServices.newKieBuilder(kfs);
        kieBuilder.buildAll();

        if (kieBuilder.getResults().hasMessages(org.kie.api.builder.Message.Level.ERROR)) {
            log.error("Custom DRL compilation errors: {}", kieBuilder.getResults().getMessages());
            throw new IllegalArgumentException("Invalid custom DRL: " + kieBuilder.getResults().getMessages());
        }

        KieModule kieModule = kieBuilder.getKieModule();
        return kieServices.newKieContainer(kieModule.getReleaseId());
    }
}
