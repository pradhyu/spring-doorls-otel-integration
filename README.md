# Spring Boot Drools + OpenTelemetry Application

A Spring Boot application featuring a **Drools Rule Engine** execution pipeline over JSON REST payloads, fully integrated with **OpenTelemetry (OTEL)** exporting traces, metrics, and logs to an OpenTelemetry Collector and **Jaeger All-in-One**.

---

## 🌟 Key Features

1. **Spring Boot REST Endpoints**:
   - `POST /api/rules/execute`: Accepts JSON requests (`RuleExecutionRequest` or `CustomerFact`) and evaluates Drools rules.
   - `POST /api/rules/evaluate-customer`: Evaluates customer facts directly.
   - `POST /api/rules/execute-json`: Accepts dynamic raw JSON facts.
   - `GET /api/rules/health`: Microservice health check.

2. **Drools 8.x/9.x Rule Engine**:
   - Business rules defined in `.drl` files (`com/example/droolsotel/rules/discount.drl`).
   - Dynamic support for customer discounts based on age, membership tier, purchase volume, and custom inline DRL rules.

3. **Full OpenTelemetry (OTEL) Integration**:
   - **Traces**: Auto-instrumented REST controllers + custom Drools spans (`drools.execution.process`, `drools.fireAllRules`, `drools.rule.<rule_name>`).
   - **Metrics**: Custom counters (`drools_rules_fired_total`, `drools_facts_total`) exported over OTLP.
   - **Logs**: Structured OpenTelemetry Logback Appender with trace context injection (`trace_id`, `span_id`) sent directly to the OTEL Collector.

4. **Observability Ecosystem (Docker Compose)**:
   - **Jaeger All-in-One** UI accessible at `http://localhost:16686`.
   - **OpenTelemetry Collector** listening on OTLP gRPC (`4317`) & OTLP HTTP (`4318`).

---

## 📁 Repository Structure

```
├── docker-compose.yml          # Container orchestration (App, OTEL Collector, Jaeger)
├── Dockerfile                  # Multi-stage Docker build for Spring Boot application
├── otel-collector-config.yaml  # OpenTelemetry Collector routing config
├── pom.xml                     # Maven dependencies (Spring Boot, Drools, OTEL SDK)
├── sample-request.json         # Sample JSON payload for testing REST POST
├── customer-input.json         # Sample JSON payload for customer evaluation
├── src
│   ├── main
│   │   ├── java/com/example/droolsotel
│   │   │   ├── DroolsOtelApplication.java
│   │   │   ├── config/
│   │   │   │   ├── DroolsConfig.java           # KieServices & KieContainer setup
│   │   │   │   └── OpenTelemetryConfig.java    # OTEL Tracer, Meter & Log Providers
│   │   │   ├── controller/
│   │   │   │   └── DroolsController.java       # REST POST endpoints
│   │   │   ├── model/
│   │   │   │   ├── CustomerFact.java           # Drools fact domain model
│   │   │   │   ├── RuleExecutionRequest.java
│   │   │   │   └── RuleExecutionResponse.java  # Output DRL response with traceId
│   │   │   ├── otel/
│   │   │   │   ├── DroolsOtelAgendaEventListener.java    # Tracing & metrics on rule fire
│   │   │   │   └── DroolsOtelRuleRuntimeEventListener.java# Fact insertion/update metrics
│   │   │   └── service/
│   │   │       └── DroolsExecutionService.java # Business logic wrapping Drools session
│   │   └── resources
│   │       ├── application.yml                 # Application & OTEL properties
│   │       ├── logback-spring.xml              # OpenTelemetry Logback appender
│   │       └── com/example/droolsotel/rules/
│   │           └── discount.drl                # Drools rules definition
│   └── test
│       └── java/com/example/droolsotel/
│           └── DroolsOtelApplicationTests.java # Integration tests with trace assertions
```

---

## 🚀 Quick Start with Docker Compose

Start the full stack (Spring Boot App + OTEL Collector + Jaeger All-in-One) with a single command:

```bash
docker-compose up --build
```

### Access Ports & Services:
- **Spring Boot Application REST API**: `http://localhost:8080`
- **Jaeger Web UI**: `http://localhost:16686`
- **OTEL Collector (OTLP gRPC)**: `http://localhost:4317`
- **OTEL Collector (OTLP HTTP)**: `http://localhost:4318`
- **Spring Actuator Metrics**: `http://localhost:8080/actuator`

---

## 🚀 Quick Start with .NET Aspire Dashboard

Alternatively, you can run the stack using the **.NET Aspire Dashboard** for telemetry visualization:

```bash
docker-compose -f docker-compose-aspire.yml up --build
```

### Access Ports & Services:
- **Spring Boot Application REST API**: `http://localhost:8080`
- **.NET Aspire Dashboard UI**: `http://localhost:18888`
- **Spring Actuator Metrics**: `http://localhost:8080/actuator`

---

## 💻 Local Development & Build

### Prerequisites
- JDK 21+
- Apache Maven 3.9+ (or `./mvnw`)

### Build & Run Tests
```bash
./mvnw clean test
```

### Run Locally
```bash
./mvnw spring-boot:run
```

---

## 🧪 Testing REST POST Endpoints

### 1. Execute Drools Rules over JSON POST (`POST /api/rules/execute`)

```bash
curl -X POST http://localhost:8080/api/rules/execute \
  -H "Content-Type: application/json" \
  -d '{
    "customer": {
      "name": "Jane Doe",
      "age": 68,
      "membershipTier": "GOLD",
      "purchaseAmount": 750.0
    }
  }'
```

#### Response Example:
```json
{
  "status": "SUCCESS",
  "customer": {
    "name": "Jane Doe",
    "age": 68,
    "membershipTier": "GOLD",
    "purchaseAmount": 750.0,
    "discountPercentage": 30.0,
    "finalAmount": 525.0,
    "appliedRules": [
      "Gold Membership Discount (+15%)",
      "Senior Customer Discount (+10%)",
      "Large Purchase Bonus Discount (+5%)"
    ]
  },
  "rulesFiredCount": 3,
  "executedRules": [
    "Gold Membership Discount (+15%)",
    "Senior Customer Discount (+10%)",
    "Large Purchase Bonus Discount (+5%)"
  ],
  "executionTimeMs": 14,
  "traceId": "75b971bbdbb8322ec802421f5553e254",
  "spanId": "8a977d7caf8a8bb6"
}
```

### 2. Evaluate Direct Customer Fact (`POST /api/rules/evaluate-customer`)

```bash
curl -X POST http://localhost:8080/api/rules/evaluate-customer \
  -H "Content-Type: application/json" \
  -d '{
    "name": "Robert Johnson",
    "age": 17,
    "membershipTier": "PLATINUM",
    "purchaseAmount": 300.0
  }'
```

### 3. Dynamic Custom DRL Evaluation

```bash
curl -X POST http://localhost:8080/api/rules/execute \
  -H "Content-Type: application/json" \
  -d '{
    "customer": {
      "name": "Alice Cooper",
      "age": 45,
      "membershipTier": "REGULAR",
      "purchaseAmount": 1200.0
    },
    "customDrl": "package com.example.droolsotel.rules;\nimport com.example.droolsotel.model.CustomerFact;\nrule \"Special Flash Sale\"\nwhen\n $c : CustomerFact( purchaseAmount > 1000.0 )\nthen\n $c.addDiscount(40.0);\n $c.addAppliedRule(\"Special Flash Sale (+40%)\");\nend\n"
  }'
```

---

## 🔍 Viewing Telemetry in Jaeger UI

1. Open `http://localhost:16686` in your browser.
2. Select Service: **`drools-otel-app`**.
3. Click **Find Traces**.
4. You will see full trace flame graphs showing:
   - Request entry via Spring MVC Controller
   - `drools.execution.process` span
   - `drools.fireAllRules` span
   - Individual rule spans (`drools.rule.Senior Customer Discount`, `drools.rule.Gold Membership Discount`) with matched attributes, metrics, and logs.
