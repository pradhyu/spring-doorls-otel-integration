package com.example.droolsotel.model;

public class RuleExecutionRequest {
    private CustomerFact customer;
    private String customDrl;

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
}
