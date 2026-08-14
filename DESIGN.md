# Architecture & Design: OpenTelemetry + Jaeger Integration

This document explains the architectural decisions behind the multi-service container orchestration setup in [`docker-compose.yml`](file:///home/pkshrestha/git/otel-spring/docker-compose.yml), focusing on trace context propagation and custom nested span lifecycles across microservice boundaries.

---

## 🏗️ System Architecture

To help visualize how data flows and how ports are exposed, here are diagrams showing both the physical container topology and the logical lifecycle of a single request trace across the microservices.

### 1. Physical Service & Port Topology
This diagram shows how containers communicate on the internal bridge network (`otel-net`) versus what is exposed to the local host machine.

```mermaid
graph LR
    subgraph Host ["Host System (Localhost)"]
        Client[Client / curl]
        Browser[Web Browser]
    end

    subgraph DockerNet ["Docker Network (otel-net)"]
        direction TB
        Proxy["proxy-service<br/>(Port: 8081)"]
        App["drools-otel-app<br/>(Port: 8080)"]
        Collector["otel-collector<br/>(Ports: 4317, 4318)"]
        Jaeger["jaeger (All-in-One)<br/>(Ports: 16686, 14250, 4317/4318 internal)"]
    end

    Client -->|HTTP POST /api/proxy/execute<br/>Port 8081:8081| Proxy
    Proxy -->|HTTP POST /api/rules/execute<br/>Port 8080| App
    Browser -->|Access UI<br/>Port 16686:16686| Jaeger
    
    Proxy -->|Export Telemetry (OTLP gRPC)<br/>Port 4317:4317| Collector
    App -->|Export Telemetry (OTLP gRPC)<br/>Port 4317:4317| Collector
    Collector -->|Forward Traces (OTLP gRPC)<br/>Port 4317:4317| Jaeger
```

#### 🖥️ Terminal Unicode Render
```text

 ┌─────────────────────────┐                                        ┌──────────────────────────────────┐
 │ Host System (Localhost) │                                        │ Docker Network (otel-net)        │
 │                         │                                        │                                  │
 │                         │                                        │                                  │
 │ ┌─────────────────┐     │                                        │ ┌──────────────────────────────┐ │
 │ │                 │     │                                        │ │                              │ │
 │ │                 │     │                                        │ │                              │ │
 │ │  Client / curl  │HTTP POST /api/proxy/execute<br/>Port 8081:8081 │   proxy-service<br/>(Port:   │ │
 │ │                 ├─────┼────────────────────────────────────────┼►│            8081)             │ │
 │ │                 │     │                                        │ │                              │ │
 │ │                 │     │                                        │ │                              │ │
 │ └─────────────────┘     │                                        │ └───────────────┬──────────────┘ │
 │                         │                HTTP POST /api/rules/execute<br/>Port 8080▼────────────────┤
 │ ┌─────────────────┐     │                                        │ ┌──────────────────────────────┐ │
 │ │                 │     │                                        │ │                              │ │
 │ │                 │     │                                        │ │                              │ │
 │ │   Web Browser   │     │                                        │ │  drools-otel-app<br/>(Port:  │ │
 │ │                 ├─────┼──────────────────╮                     │ │            8080)             │ │
 │ │                 │     │                  │                     │ │                              │ │
 │ │                 │     │                  │                     │ │                              │ │
 │ └─────────────────┘     │                  │                     │ └────────────────┬─────────────┘ │
 │                         │                  │                     │                  │               │
 └─────────────────────────┘                  │                     │                  │               │
                                              │                     │                  │               │
                                              │                     │                  │               │
                                              │                     │                ╭─┼───────────────┤
                                        Export│Telemetry (OTLP gRPC)<br/>Port 4317:4317│               │
                                      Export Telemetry (OTLP gRPC)<br/>Port 4317:4317│ │               │
                                              │                     │                ▼ ▼               │
                                              │Access UI<br/>Port 1668┌──────────────────────────────┐ │
                                              │                     │ │                              │ │
                                              │                     │ │                              │ │
                                              │                     │ │  otel-collector<br/>(Ports:  │ │
                                              │                     │ │         4317, 4318)          │ │
                                              │                     │ │                              │ │
                                              │                     │ │                              │ │
                                              │                     │ └───────────────┬──────────────┘ │
                                         Forward Traces (OTLP gRPC)<br/>Port 4317:4317▼                │
                                              │                     │ ┌──────────────────────────────┐ │
                                              │                     │ │                              │ │
                                              │                     │ │            jaeger            │ │
                                              │                     │ │   (All-in-One)<br/>(Ports:   │ │
                                              ╰─────────────────────┼►│        16686, 14250,         │ │
                                                                    │ │     4317/4318 internal)      │ │
                                                                    │ │                              │ │
                                                                    │ └──────────────────────────────┘ │
                                                                    │                                  │
                                                                    └──────────────────────────────────┘
```



### 2. Logical Telemetry Request Flow (Context Propagation & Child Spans)
This diagram details the sequence of execution inside both containers, demonstrating how the trace context is passed via `traceparent` headers to link the spans into a single trace graph, including the local child spans in the proxy service.

```mermaid
sequenceDiagram
    autonumber
    actor Client
    participant Proxy as ProxyController
    participant App as Spring MVC Controller
    participant Svc as DroolsExecutionService
    participant Drools as Drools Rule Engine
    participant Listener as DroolsOtelAgendaEventListener
    participant Collector as OTEL Collector
    participant Jaeger as Jaeger

    Client->>Proxy: POST /api/proxy/execute (customer data)
    Note over Proxy: Start Span: "proxy.execute"
    
    Note over Proxy: Start Span: "proxy.prepare_request" (Validation)
    Note over Proxy: Close Span: "proxy.prepare_request"
    
    Note over Proxy: Start Span: "proxy.http_call_to_drools"
    Note over Proxy: Inject W3C Context in headers (traceparent)
    
    Proxy->>App: POST /api/rules/execute (customer data + headers)
    Note over App: Extract context & Start Span: "http post /api/rules/execute"
    App->>Svc: execute(request)
    Note over Svc: Start Span: "drools.execution.process"
    Svc->>Drools: fireAllRules()
    Note over Drools: Start Span: "drools.fireAllRules"
    
    loop Rule Evaluation
        Drools->>Listener: rule matches agenda
        Note over Listener: Start Span: "drools.engine.evaluate"
        Drools->>Drools: execute rule consequence
        Note over Listener: Start Span: "drools.rule.<name>"
        Drools->>Listener: rule execution ends
        Note over Listener: Close Span: "drools.rule.<name>"
        Note over Listener: Close Span: "drools.engine.evaluate"
    end
    
    Drools-->>Svc: fireAllRules finished
    Note over Svc: Close Span: "drools.fireAllRules"
    Svc-->>App: response object
    Note over App: Close Span: "drools.execution.process"
    App-->>Proxy: HTTP 200 OK + JSON Response
    Note over App: Close Span: "http post /api/rules/execute"
    
    Note over Proxy: Close Span: "proxy.http_call_to_drools"
    
    Note over Proxy: Start Span: "proxy.process_response" (Mappers)
    Note over Proxy: Close Span: "proxy.process_response"
    
    Note over Proxy: Close Span: "proxy.execute"
    Proxy-->>Client: HTTP 200 OK + JSON (with trace ID details)

    Note over App: Batch Span Exporters trigger (async)
    App-xCollector: OTLP Push Traces
    Proxy-xCollector: OTLP Push Traces
    Collector-xJaeger: Forward Traces (OTLP/gRPC)
```

#### 🖥️ Terminal Unicode Render
```text
     O
    /|\
    / \                                          ┌───────────────┐                                      ┌─────────────────────┐ ┌──────────────────────┐        ┌──────────────────┐   ┌─────────────────────────────┐ ┌──────────────┐                        ┌──────┐
                                                 │ProxyController│                                      │Spring MVC Controller│ │DroolsExecutionService│        │Drools Rule Engine│   │DroolsOtelAgendaEventListener│ │OTEL Collector│                        │Jaeger│
  Client                                         └───────────────┘                                      └─────────────────────┘ └──────────────────────┘        └──────────────────┘   └─────────────────────────────┘ └──────────────┘                        └──────┘
     ┆ 1: POST /api/proxy/execute (customer data)        ┆                                                         ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ────────────────────────────────────────────────────►                                                         ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                                         ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                    ┌─────────────────────────────┐                                          ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                    │ Start Span: "proxy.execute" │                                          ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                    └─────────────────────────────┘                                          ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                                         ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                         ┌──────────────────────────────────────────────────┐                                ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                         │ Start Span: "proxy.prepare_request" (Validation) │                                ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                         └──────────────────────────────────────────────────┘                                ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                                         ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                ┌─────────────────────────────────────┐                                      ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                │ Close Span: "proxy.prepare_request" │                                      ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                └─────────────────────────────────────┘                                      ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                                         ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                              ┌─────────────────────────────────────────┐                                    ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                              │ Start Span: "proxy.http_call_to_drools" │                                    ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                              └─────────────────────────────────────────┘                                    ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                                         ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                            ┌─────────────────────────────────────────────┐                                  ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                            │ Inject W3C Context in headers (traceparent) │                                  ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                            └─────────────────────────────────────────────┘                                  ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆ 2: POST /api/rules/execute (customer data + headers)    ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                                   ──────────────────────────────────────────────────────────►                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                                         ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                         ┌──────────────────────────────────────────────────────────────┐                       ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                         │ Extract context & Start Span: "http post /api/rules/execute" │                       ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                         └──────────────────────────────────────────────────────────────┘                       ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                                         ┆ 3: execute(request)    ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                                         ─────────────────────────►                             ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                                         ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                                         ┆   ┌────────────────────────────────────────┐         ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                                         ┆   │ Start Span: "drools.execution.process" │         ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                                         ┆   └────────────────────────────────────────┘         ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                                         ┆                        ┆ 4: fireAllRules()           ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                                         ┆                        ──────────────────────────────►                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                                         ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                                         ┆                        ┆           ┌───────────────────────────────────┐         ┆                        ┆                                   ┆
     ┆                                                   ┆                                                         ┆                        ┆           │ Start Span: "drools.fireAllRules" │         ┆                        ┆                                   ┆
     ┆                                                   ┆                                                         ┆                        ┆           └───────────────────────────────────┘         ┆                        ┆                                   ┆
     ┆                                                   ┆                                                         ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
┌────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┐
│[loop] Rule Evaluation                                                                                                                                                                                                                                                  │
│    ┆                                                   ┆                                                         ┆                        ┆                             ┆ 5: rule matches agenda    ┆                        ┆                                   ┆     │
│    ┆                                                   ┆                                                         ┆                        ┆                             ────────────────────────────►                        ┆                                   ┆     │
│    ┆                                                   ┆                                                         ┆                        ┆                             ┆                           ┆                        ┆                                   ┆     │
│    ┆                                                   ┆                                                         ┆                        ┆                             ┆       ┌──────────────────────────────────────┐     ┆                                   ┆     │
│    ┆                                                   ┆                                                         ┆                        ┆                             ┆       │ Start Span: "drools.engine.evaluate" │     ┆                                   ┆     │
│    ┆                                                   ┆                                                         ┆                        ┆                             ┆       └──────────────────────────────────────┘     ┆                                   ┆     │
│    ┆                                                   ┆                                                         ┆                        ┆                             ┆ 6: execute rule consequence                        ┆                                   ┆     │
│    ┆                                                   ┆                                                         ┆                        ┆                             ┆─────────────────────────────┐                      ┆                                   ┆     │
│    ┆                                                   ┆                                                         ┆                        ┆                             ◄─────────────────────────────┘                      ┆                                   ┆     │
│    ┆                                                   ┆                                                         ┆                        ┆                             ┆                           ┆                        ┆                                   ┆     │
│    ┆                                                   ┆                                                         ┆                        ┆                             ┆         ┌──────────────────────────────────┐       ┆                                   ┆     │
│    ┆                                                   ┆                                                         ┆                        ┆                             ┆         │ Start Span: "drools.rule.<name>" │       ┆                                   ┆     │
│    ┆                                                   ┆                                                         ┆                        ┆                             ┆         └──────────────────────────────────┘       ┆                                   ┆     │
│    ┆                                                   ┆                                                         ┆                        ┆                             ┆ 7: rule execution ends    ┆                        ┆                                   ┆     │
│    ┆                                                   ┆                                                         ┆                        ┆                             ────────────────────────────►                        ┆                                   ┆     │
│    ┆                                                   ┆                                                         ┆                        ┆                             ┆                           ┆                        ┆                                   ┆     │
│    ┆                                                   ┆                                                         ┆                        ┆                             ┆         ┌──────────────────────────────────┐       ┆                                   ┆     │
│    ┆                                                   ┆                                                         ┆                        ┆                             ┆         │ Close Span: "drools.rule.<name>" │       ┆                                   ┆     │
│    ┆                                                   ┆                                                         ┆                        ┆                             ┆         └──────────────────────────────────┘       ┆                                   ┆     │
│    ┆                                                   ┆                                                         ┆                        ┆                             ┆                           ┆                        ┆                                   ┆     │
│    ┆                                                   ┆                                                         ┆                        ┆                             ┆       ┌──────────────────────────────────────┐     ┆                                   ┆     │
│    ┆                                                   ┆                                                         ┆                        ┆                             ┆       │ Close Span: "drools.engine.evaluate" │     ┆                                   ┆     │
│    ┆                                                   ┆                                                         ┆                        ┆                             ┆       └──────────────────────────────────────┘     ┆                                   ┆     │
│    ┆                                                   ┆                                                         ┆                        ┆                             ┆                           ┆                        ┆                                   ┆     │
└────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────┘
     ┆                                                   ┆                                                         ┆                        ┆ 8: fireAllRules finished    ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                                         ┆                        ◄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                                         ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                                         ┆      ┌───────────────────────────────────┐           ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                                         ┆      │ Close Span: "drools.fireAllRules" │           ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                                         ┆      └───────────────────────────────────┘           ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                                         ┆ 9: response object     ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                                         ◄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄                             ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                                         ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                    ┌────────────────────────────────────────┐    ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                    │ Close Span: "drools.execution.process" │    ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                    └────────────────────────────────────────┘    ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆ 10: HTTP 200 OK + JSON Response                         ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                                   ◄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                                         ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                  ┌────────────────────────────────────────────┐  ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                  │ Close Span: "http post /api/rules/execute" │  ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                  └────────────────────────────────────────────┘  ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                                         ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                              ┌─────────────────────────────────────────┐                                    ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                              │ Close Span: "proxy.http_call_to_drools" │                                    ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                              └─────────────────────────────────────────┘                                    ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                                         ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                          ┌────────────────────────────────────────────────┐                                 ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                          │ Start Span: "proxy.process_response" (Mappers) │                                 ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                          └────────────────────────────────────────────────┘                                 ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                                         ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                               ┌──────────────────────────────────────┐                                      ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                               │ Close Span: "proxy.process_response" │                                      ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                               └──────────────────────────────────────┘                                      ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                                         ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                    ┌─────────────────────────────┐                                          ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                    │ Close Span: "proxy.execute" │                                          ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                    └─────────────────────────────┘                                          ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆ 11: HTTP 200 OK + JSON (with trace ID details)    ┆                                                         ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ◄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄┄                                                         ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                                         ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                     ┌──────────────────────────────────────┐     ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                     │ Batch Span Exporters trigger (async) │     ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                     └──────────────────────────────────────┘     ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                                         ┆ 12: OTLP Push Traces   ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                                   ┆                                                         ────────────────────────────────────────────────────────────────────────────────────────────────────────────x                                   ┆
     ┆                                                   ┆ 13: OTLP Push Traces                                    ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
     ┆                                                   ──────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────────x                                   ┆
     ┆                                                   ┆                                                         ┆                        ┆                             ┆                           ┆                        ┆ 14: Forward Traces (OTLP/gRPC)    ┆
     ┆                                                   ┆                                                         ┆                        ┆                             ┆                           ┆                        ────────────────────────────────────x
     ┆                                                   ┆                                                         ┆                        ┆                             ┆                           ┆                        ┆                                   ┆
```


---

## ❓ Why Use both an OTEL Collector and Jaeger?

You might wonder: *Since `jaegertracing/all-in-one` has its own OTLP receiver (via `COLLECTOR_OTLP_ENABLED=true`), why do we need the standalone `otel-collector` container in the middle?*

Here is why this multi-stage architecture is used:

### 1. Decoupling & Vendor Neutrality (Production Best Practice)
The application code does not know about Jaeger. It is configured to export telemetry using the standard OpenTelemetry Protocol (OTLP) to a local endpoint (`http://otel-collector:4317`). 
* If you decide to migrate from Jaeger to another backend (e.g., Datadog, Prometheus/Grafana, Honeycomb, or AWS X-Ray), **zero application changes or redeployments** are required. You only change the egress configuration in [`otel-collector-config.yaml`](file:///home/pkshrestha/git/otel-spring/otel-collector-config.yaml).

### 2. Multi-Destination Routing (Multiplexing)
Jaeger is a tracing-only tool. However, an application generates three pillars of observability: Traces, Metrics, and Logs.
* **Standalone Jaeger** cannot receive or store metrics and logs.
* **The OTEL Collector** acts as a traffic controller:
  * **Traces** are sent to Jaeger and written to the debug console.
  * **Metrics** and **Logs** can be piped to Prometheus, Elasticsearch, Loki, or standard output.

### 3. Pipeline Processing (Batching, Filtering, and Limiting)
The OTEL Collector offers high-performance processing components (like those configured in [`otel-collector-config.yaml`](file:///home/pkshrestha/git/otel-spring/otel-collector-config.yaml)):
* **`memory_limiter`**: Drop telemetry if the collector is running out of memory, protecting the system from crashing under load spikes.
* **`batch`**: Batches traces before exporting them. This drastically reduces network overhead and connection churn compared to sending every span individually from the app.
* **Sampling**: Can be configured to filter out noise (e.g., discard successful health checks and only keep failed ones).

---

## 🔗 Trace Context Propagation Mechanics

Distributed Tracing relies on **Trace Context Propagation** to stitch together separate service executions. 

1. **Extracting Incoming Headers**:
   When the Proxy Controller receives a request, it uses `GlobalOpenTelemetry.getPropagators().getTextMapPropagator().extract()` to read W3C headers (specifically `traceparent`). If none are present (e.g., initial client request), it starts a new trace.
2. **Creating Child Spans**:
   The Proxy Controller creates a local span (`proxy.execute`) parented by the extracted context.
3. **Injecting Headers**:
   Before making the outgoing call to the Drools application, it injects the current span context into the HTTP headers of the outgoing call using `getTextMapPropagator().inject()`.
4. **W3C Format Specification**:
   The `traceparent` header propagated downstream looks like:
   `00-be8ef0f36811ffff30d555afdaba1aa0-39ce6205dff2d6ce-01`
   * `00`: Version
   * `be8ef0f36811ffff30d555afdaba1aa0`: Trace ID (shared across all microservices)
   * `39ce6205dff2d6ce`: Parent Span ID (from the Proxy service)
   * `01`: Trace Flags (sampled/recorded)

---

## 🔌 How `docker-compose.yml` Configures the services

Let's look at how the containers interact inside the compose file:

### 1. The Proxy Service (`proxy`)
* **Ports**: Exposes `8081:8081` to the host.
* **Environment**:
  * `OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317`
  * `DROOLS_APP_URL=http://drools-otel-app:8080/api/rules/execute`

### 2. The Drools Application (`app`)
* **Ports**: Exposes `8080:8080` to the host.
* **Environment**:
  * `OTEL_EXPORTER_OTLP_ENDPOINT=http://otel-collector:4317`

### 3. The OpenTelemetry Collector (`otel-collector`)
* **Ports**: Exposes `4317` (gRPC) and `4318` (HTTP) to the internal network.
* **Flow**: Collects telemetry from both `proxy` and `app`, batches it, and forwards traces to `jaeger:4317`.

### 4. Jaeger (`jaeger`)
* **Ports**: Exposes `16686` for the Web UI.
* **Flow**: Stores traces and renders the flame graph showing `proxy-service` calls parented to `drools-otel-app` calls.
