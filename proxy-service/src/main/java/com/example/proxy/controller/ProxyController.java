package com.example.proxy.controller;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.Scope;
import io.opentelemetry.context.propagation.TextMapGetter;
import io.opentelemetry.context.propagation.TextMapSetter;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/proxy")
public class ProxyController {

    private static final Logger log = LoggerFactory.getLogger(ProxyController.class);

    private final OpenTelemetry openTelemetry;
    private final Tracer tracer;
    private final RestTemplate restTemplate;

    @Value("${drools.app.url}")
    private String droolsAppUrl;

    public ProxyController(OpenTelemetry openTelemetry, Tracer tracer) {
        this.openTelemetry = openTelemetry;
        this.tracer = tracer;
        this.restTemplate = new RestTemplate();
    }

    // TextMapGetter to extract headers from the incoming servlet request
    private static final TextMapGetter<HttpServletRequest> getter = new TextMapGetter<>() {
        @Override
        public Iterable<String> keys(HttpServletRequest carrier) {
            return () -> new java.util.Iterator<>() {
                private final java.util.Enumeration<String> names = carrier.getHeaderNames();
                @Override
                public boolean hasNext() { return names.hasMoreElements(); }
                @Override
                public String next() { return names.nextElement(); }
            };
        }

        @Override
        public String get(HttpServletRequest carrier, String key) {
            return carrier.getHeader(key);
        }
    };

    // TextMapSetter to inject trace parent headers into the outgoing HTTP request
    private static final TextMapSetter<HttpHeaders> setter = new TextMapSetter<>() {
        @Override
        public void set(HttpHeaders carrier, String key, String value) {
            if (carrier != null) {
                carrier.set(key, value);
            }
        }
    };

    @SuppressWarnings("unchecked")
    @PostMapping("/execute")
    public ResponseEntity<Map> proxyExecute(@RequestBody Map<String, Object> requestBody, HttpServletRequest servletRequest) {
        // 1. Extract context from incoming HTTP headers
        Context parentContext = openTelemetry.getPropagators().getTextMapPropagator().extract(Context.current(), servletRequest, getter);

        // 2. Start root proxy span
        Span rootSpan = tracer.spanBuilder("proxy.execute")
                .setParent(parentContext)
                .startSpan();

        // Retrieve or generate Request ID & Transaction ID
        String transactionId = requestBody.containsKey("transactionId") ? String.valueOf(requestBody.get("transactionId")) : null;
        String requestId = requestBody.containsKey("requestId") ? String.valueOf(requestBody.get("requestId")) : null;
        if (requestId == null || requestId.isEmpty()) {
            requestId = "req-dummy-" + UUID.randomUUID().toString().substring(0, 8);
        }

        // Attach tags (attributes) to root span
        rootSpan.setAttribute("app.request_id", requestId);
        if (transactionId != null) {
            rootSpan.setAttribute("app.transaction_id", transactionId);
        }

        log.info("Proxy service executing. Active TraceID: {}, SpanID: {}, RequestID: {}, TransactionID: {}", 
                rootSpan.getSpanContext().getTraceId(), 
                rootSpan.getSpanContext().getSpanId(),
                requestId,
                transactionId);

        ResponseEntity<Map> response = null;
        Map<String, Object> body = null;

        try (Scope scope = rootSpan.makeCurrent()) {
            
            // --- 3. BEFORE DOWNSTREAM CALL: Prep and validate payload ---
            Span prepSpan = tracer.spanBuilder("proxy.prepare_request")
                    .setParent(Context.current().with(rootSpan))
                    .startSpan();
            
            prepSpan.setAttribute("app.request_id", requestId);
            if (transactionId != null) {
                prepSpan.setAttribute("app.transaction_id", transactionId);
            }

            try (Scope prepScope = prepSpan.makeCurrent()) {
                log.info("[Span: proxy.prepare_request] Validating payload and setting attributes...");
                if (requestBody.containsKey("customer")) {
                    Map<String, Object> customer = (Map<String, Object>) requestBody.get("customer");
                    prepSpan.setAttribute("customer.name", String.valueOf(customer.get("name")));
                    prepSpan.setAttribute("customer.membership", String.valueOf(customer.get("membershipTier")));
                }
                Thread.sleep(20); // Simulate local processing/validation delay
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                prepSpan.end();
            }

            // --- 4. THE DOWNSTREAM CALL: HTTP request to Drools app ---
            Span httpCallSpan = tracer.spanBuilder("proxy.http_call_to_drools")
                    .setParent(Context.current().with(rootSpan))
                    .startSpan();
            
            httpCallSpan.setAttribute("app.request_id", requestId);
            if (transactionId != null) {
                httpCallSpan.setAttribute("app.transaction_id", transactionId);
            }

            try (Scope httpScope = httpCallSpan.makeCurrent()) {
                HttpHeaders headers = new HttpHeaders();
                headers.setContentType(MediaType.APPLICATION_JSON);

                // Inject HTTP trace context based on httpCallSpan
                openTelemetry.getPropagators().getTextMapPropagator().inject(Context.current(), headers, setter);
                
                log.info("[Span: proxy.http_call_to_drools] Outgoing traceparent: {}", headers.getFirst("traceparent"));
                
                // Propagate transactionId and requestId inside the JSON body map as well
                requestBody.put("requestId", requestId);
                if (transactionId != null) {
                    requestBody.put("transactionId", transactionId);
                }

                HttpEntity<Map<String, Object>> entity = new HttpEntity<>(requestBody, headers);
                response = restTemplate.postForEntity(droolsAppUrl, entity, Map.class);
            } finally {
                httpCallSpan.end();
            }

            // --- 5. AFTER DOWNSTREAM CALL: Post-processing and formatting response ---
            Span postSpan = tracer.spanBuilder("proxy.process_response")
                    .setParent(Context.current().with(rootSpan))
                    .startSpan();
            
            postSpan.setAttribute("app.request_id", requestId);
            if (transactionId != null) {
                postSpan.setAttribute("app.transaction_id", transactionId);
            }

            try (Scope postScope = postSpan.makeCurrent()) {
                log.info("[Span: proxy.process_response] Extracting rules engine response and injecting trace references...");
                if (response != null) {
                    body = response.getBody();
                    if (body != null) {
                        body.put("proxyTraceId", rootSpan.getSpanContext().getTraceId());
                        body.put("proxySpanId", rootSpan.getSpanContext().getSpanId());
                        body.put("requestId", requestId);
                        
                        postSpan.setAttribute("rules.fired.count", String.valueOf(body.get("rulesFiredCount")));
                        postSpan.setAttribute("execution.status", String.valueOf(body.get("status")));
                    }
                }
                Thread.sleep(15); // Simulate local response mapping delay
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            } finally {
                postSpan.end();
            }

            if (response != null && body != null) {
                return ResponseEntity.status(response.getStatusCode()).body(body);
            } else {
                return ResponseEntity.status(500).build();
            }

        } catch (Exception e) {
            log.error("Error during proxy execution", e);
            rootSpan.recordException(e);
            throw e;
        } finally {
            rootSpan.end();
        }
    }
}
