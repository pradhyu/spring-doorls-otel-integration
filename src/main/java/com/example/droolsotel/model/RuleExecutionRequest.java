package com.example.droolsotel.model;

public class RuleExecutionRequest {
    private CustomerFact customer;
    private String customDrl;
    private String transactionId;
    private String requestId;

    public RuleExecutionRequest() {}

    public RuleExecutionRequest(CustomerFact customer) {
        this.customer = customer;
    }

    public CustomerFact getCustomer() {
        return customer;
    }

    public void setCustomer(CustomerFact customer) {
        this.customer = customer;
    }

    public String getCustomDrl() {
        return customDrl;
    }

    public void setCustomDrl(String customDrl) {
        this.customDrl = customDrl;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
}
