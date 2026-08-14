# Workspace Rules & Context: OpenTelemetry + Drools Integration

This file provides context and guidelines for coding assistants (agents) working in this repository.

---

## 🏗️ Project Architecture & Context

This project integrates **Drools Rule Engine (v8/v9)** with **OpenTelemetry (OTel) Distributed Tracing** in a multi-service Spring Boot environment.

### 1. Service Topology
* **`proxy-service` (Port `8081`)**: The entry-point API gateway. Receives requests, creates parent tracing context, injects W3C `traceparent` context, and forwards requests to the Drools application.
* **`drools-otel-app` (Port `8080`)**: The core business rules engine. Evaluates facts, retrieves data from the HSQL database, and fires Drools rules.
* **`otel-collector` (Ports `4317`/`4318`)**: Gathers telemetry from both services and forwards it downstream.
* **`jaeger` (Port `16686`)**: Visualization UI for spans and distributed trace graphs.

### 2. Distributed Tracing & Context Propagation
* **Headers**: Context is propagated downstream via standard W3C `traceparent` headers.
* **Span Attributes (Tags)**: Every span in the trace carries two crucial business identifiers:
  * `app.request_id` (e.g. `req-dummy-xxxxxxxx` or client request ID)
  * `app.transaction_id` (e.g. `TX-xxxxxx` passed in the request body)
* **Local Spans in Proxy**: Execution blocks in the proxy controller are instrumented as child spans:
  * `proxy.prepare_request`
  * `proxy.http_call_to_drools`
  * `proxy.process_response`

### 3. Drools Rule-Flow Phases & Agenda Groups
To coordinate execution and capture sequential flow, the rules in [`discount.drl`](file:///home/pkshrestha/git/otel-spring/src/main/resources/com/example/droolsotel/rules/discount.drl) are organized into four agenda groups:
1. **`prepare`**: Database pre-fetching logic (membership level, tier discount percentage).
2. **`business-rules`**: Core business rules (loyalty points, senior/youth age checks).
3. **`customization`**: Coupon generation and conflict resolution (disabling lower value coupons).
4. **`post-processing`**: Coupon application and final totals calculation.

These are executed in series inside [`DroolsExecutionService.java`](file:///home/pkshrestha/git/otel-spring/src/main/java/com/example/droolsotel/service/DroolsExecutionService.java), with each phase running under its own OpenTelemetry span (`drools.phase.<phase>`).

### 4. Logging & Diagnostics
* **Spans Events**: Fact updates and matches are recorded as OpenTelemetry Span Events (`span.addEvent(...)`) inside the OTel event listeners.
* **DRL Rules Logger**: Logging inside rules is done through an SLF4J logger global named `logger` injected into the KieSession. **Never use `System.out.println` inside rules.**

---

## 📋 Guidelines for Future Agents

1. **Context Propagation Integrity**:
   * When editing the gateway controller ([`ProxyController.java`](file:///home/pkshrestha/git/otel-spring/proxy-service/src/main/java/com/example/proxy/controller/ProxyController.java)) or the downstream REST client, ensure the `traceparent` headers are correctly injected using the `TextMapPropagator`.
2. **Span Tagging Conventions**:
   * If you introduce any new spans in the Java services, ensure you extract `requestId` and `transactionId` and attach them as attributes:
     ```java
     span.setAttribute("app.request_id", requestId);
     span.setAttribute("app.transaction_id", transactionId);
     ```
3. **Rule-Flow Modifications**:
   * If you add new rules to [`discount.drl`](file:///home/pkshrestha/git/otel-spring/src/main/resources/com/example/droolsotel/rules/discount.drl), assign them to one of the four agenda-groups (`prepare`, `business-rules`, `customization`, or `post-processing`) so they are evaluated in sequence.
4. **Logger Rules**:
   * When writing rules, always declare `global org.slf4j.Logger logger;` and log statements using `logger.info("Message {}", param);` instead of raw stdout.
5. **Robustness for Custom DRLs**:
   * The application supports executing custom DRL strings dynamically. Since custom DRLs may not declare the custom agenda groups or logger globals:
     * Check that the agenda group exists before focusing it. If running a custom DRL with no custom phases, default to firing the `"MAIN"` agenda group.
     * Wrap global variable assignments (like `dbService` and `logger`) in `try-catch` blocks to swallow `RuntimeException`s if the custom DRL has not declared those globals.
