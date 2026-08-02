package com.example.droolsotel.model;

import java.util.List;

public class RuleExecutionResponse {
    private String status;
    private CustomerFact customer;
    private int rulesFiredCount;
    private List<String> executedRules;
    private long executionTimeMs;
    private String traceId;
    private String spanId;

    public RuleExecutionResponse() {}

    public RuleExecutionResponse(String status, CustomerFact customer, int rulesFiredCount,
                                 List<String> executedRules, long executionTimeMs,
                                 String traceId, String spanId) {
        this.status = status;
        this.customer = customer;
        this.rulesFiredCount = rulesFiredCount;
        this.executedRules = executedRules;
        this.executionTimeMs = executionTimeMs;
        this.traceId = traceId;
        this.spanId = spanId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public CustomerFact getCustomer() {
        return customer;
    }

    public void setCustomer(CustomerFact customer) {
        this.customer = customer;
    }

    public int getRulesFiredCount() {
        return rulesFiredCount;
    }

    public void setRulesFiredCount(int rulesFiredCount) {
        this.rulesFiredCount = rulesFiredCount;
    }

    public List<String> getExecutedRules() {
        return executedRules;
    }

    public void setExecutedRules(List<String> executedRules) {
        this.executedRules = executedRules;
    }

    public long getExecutionTimeMs() {
        return executionTimeMs;
    }

    public void setExecutionTimeMs(long executionTimeMs) {
        this.executionTimeMs = executionTimeMs;
    }

    public String getTraceId() {
        return traceId;
    }

    public void setTraceId(String traceId) {
        this.traceId = traceId;
    }

    public String getSpanId() {
        return spanId;
    }

    public void setSpanId(String spanId) {
        this.spanId = spanId;
    }
}
