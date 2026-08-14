package com.example.droolsotel.controller;

import com.example.droolsotel.model.CustomerFact;
import com.example.droolsotel.model.RuleExecutionRequest;
import com.example.droolsotel.model.RuleExecutionResponse;
import com.example.droolsotel.service.DroolsExecutionService;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/rules")
public class DroolsController {

    private static final Logger log = LoggerFactory.getLogger(DroolsController.class);

    private final DroolsExecutionService executionService;
    private final Tracer tracer;
    private final ObjectMapper objectMapper;

    public DroolsController(DroolsExecutionService executionService, Tracer tracer, ObjectMapper objectMapper) {
        this.executionService = executionService;
        this.tracer = tracer;
        this.objectMapper = objectMapper;
    }

    /**
     * REST POST Endpoint accepting JSON input for Drools Rule Execution.
     * Endpoint: POST /api/rules/execute
     * Payload: RuleExecutionRequest or CustomerFact JSON
     */
    @PostMapping(value = "/execute", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RuleExecutionResponse> executeRules(@RequestBody RuleExecutionRequest request) {
        Span span = tracer.spanBuilder("http.post.rules.execute")
                .setAttribute("http.method", "POST")
                .setAttribute("http.route", "/api/rules/execute")
                .startSpan();

        String requestId = request.getRequestId();
        if (requestId == null || requestId.isEmpty()) {
            requestId = "req-dummy-" + java.util.UUID.randomUUID().toString().substring(0, 8);
        }
        span.setAttribute("app.request_id", requestId);

        if (request.getTransactionId() != null) {
            span.setAttribute("app.transaction_id", request.getTransactionId());
        }

        try (Scope scope = span.makeCurrent()) {
            log.info("Received REST POST /api/rules/execute request [TraceID: {}, RequestID: {}]", 
                    span.getSpanContext().getTraceId(), requestId);
            RuleExecutionResponse response = executionService.executeRules(request);
            response.setTraceId(span.getSpanContext().getTraceId());
            response.setSpanId(span.getSpanContext().getSpanId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            span.recordException(e);
            span.setAttribute("error", true);
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * REST POST Endpoint accepting direct Customer Fact JSON.
     * Endpoint: POST /api/rules/evaluate-customer
     */
    @PostMapping(value = "/evaluate-customer", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RuleExecutionResponse> evaluateCustomer(@RequestBody CustomerFact customer) {
        Span span = tracer.spanBuilder("http.post.rules.evaluate-customer")
                .setAttribute("http.method", "POST")
                .setAttribute("http.route", "/api/rules/evaluate-customer")
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            log.info("Received REST POST /api/rules/evaluate-customer for: {}", customer.getName());
            RuleExecutionRequest request = new RuleExecutionRequest(customer);
            RuleExecutionResponse response = executionService.executeRules(request);
            response.setTraceId(span.getSpanContext().getTraceId());
            response.setSpanId(span.getSpanContext().getSpanId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            span.recordException(e);
            span.setAttribute("error", true);
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * REST POST Endpoint accepting raw generic JSON fact payload.
     * Endpoint: POST /api/rules/execute-json
     */
    @PostMapping(value = "/execute-json", consumes = MediaType.APPLICATION_JSON_VALUE, produces = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RuleExecutionResponse> executeRawJson(@RequestBody Map<String, Object> jsonMap) {
        Span span = tracer.spanBuilder("http.post.rules.execute-json")
                .setAttribute("http.method", "POST")
                .setAttribute("http.route", "/api/rules/execute-json")
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            log.info("Received REST POST /api/rules/execute-json payload: {}", jsonMap);
            CustomerFact customer = objectMapper.convertValue(jsonMap, CustomerFact.class);
            RuleExecutionRequest request = new RuleExecutionRequest(customer);
            RuleExecutionResponse response = executionService.executeRules(request);
            response.setTraceId(span.getSpanContext().getTraceId());
            response.setSpanId(span.getSpanContext().getSpanId());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            span.recordException(e);
            span.setAttribute("error", true);
            throw e;
        } finally {
            span.end();
        }
    }

    /**
     * Health check endpoint
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, String>> health() {
        return ResponseEntity.ok(Map.of(
                "status", "UP",
                "service", "drools-otel-app",
                "opentelemetry", "ENABLED"
        ));
    }
}
