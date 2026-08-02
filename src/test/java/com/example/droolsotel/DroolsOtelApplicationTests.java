package com.example.droolsotel;

import com.example.droolsotel.model.CustomerFact;
import com.example.droolsotel.model.RuleExecutionRequest;
import com.example.droolsotel.model.RuleExecutionResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DroolsOtelApplicationTests {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void contextLoads() {
    }

    @Test
    void testExecuteRulesSeniorGoldCustomer() {
        CustomerFact customer = new CustomerFact("Alice Smith", 70, "GOLD", 300.0);
        RuleExecutionRequest request = new RuleExecutionRequest(customer);

        ResponseEntity<RuleExecutionResponse> responseEntity = restTemplate.postForEntity(
                "/api/rules/execute",
                request,
                RuleExecutionResponse.class
        );

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        RuleExecutionResponse response = responseEntity.getBody();
        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo("SUCCESS");
        assertThat(response.getRulesFiredCount()).isGreaterThanOrEqualTo(2);
        
        CustomerFact updatedCustomer = response.getCustomer();
        assertThat(updatedCustomer.getAppliedRules()).contains(
                "Senior Customer Discount (+10%)",
                "Database Tier Discount",
                "Applied Coupon: LOYALTY_BONUS (+10.0%)"
        );
        // Total discount = 10% (Senior) + 15% (Gold DB) + 10% (Coupon) = 35% -> Final price = 300 * (1 - 0.35) = 195.0
        assertThat(updatedCustomer.getDiscountPercentage()).isEqualTo(35.0);
        assertThat(updatedCustomer.getFinalAmount()).isEqualTo(195.0);

        assertThat(response.getTraceId()).isNotNull().isNotBlank();
        assertThat(response.getSpanId()).isNotNull().isNotBlank();
    }

    @Test
    void testEvaluateCustomerEndpoint() {
        CustomerFact customer = new CustomerFact("Bob Junior", 16, "REGULAR", 100.0);

        ResponseEntity<RuleExecutionResponse> responseEntity = restTemplate.postForEntity(
                "/api/rules/evaluate-customer",
                customer,
                RuleExecutionResponse.class
        );

        assertThat(responseEntity.getStatusCode()).isEqualTo(HttpStatus.OK);
        RuleExecutionResponse response = responseEntity.getBody();
        assertThat(response).isNotNull();
        assertThat(response.getCustomer().getDiscountPercentage()).isEqualTo(15.0);
        assertThat(response.getCustomer().getFinalAmount()).isEqualTo(85.0);
    }
}
