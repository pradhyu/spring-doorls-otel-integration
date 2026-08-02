package com.example.droolsotel.service;

import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public class CustomerDbService {

    private final JdbcTemplate jdbcTemplate;
    private final Tracer tracer;

    public CustomerDbService(JdbcTemplate jdbcTemplate, Tracer tracer) {
        this.jdbcTemplate = jdbcTemplate;
        this.tracer = tracer;
    }

    public String getMembershipTier(String memberId) {
        if (memberId == null || memberId.isBlank()) {
            return "REGULAR";
        }

        Span span = tracer.spanBuilder("db.getMembershipTier")
                .setAttribute(AttributeKey.stringKey("db.system"), "hsqldb")
                .setAttribute(AttributeKey.stringKey("db.statement"), "SELECT membership_tier FROM member_tier WHERE member_id = ?")
                .setAttribute(AttributeKey.stringKey("db.member_id"), memberId)
                .startSpan();

        try (Scope scope = span.makeCurrent()) {
            span.addEvent("db_call_start");
            String tier = jdbcTemplate.queryForObject(
                    "SELECT membership_tier FROM member_tier WHERE member_id = ?",
                    String.class,
                    memberId
            );
            span.setAttribute(AttributeKey.stringKey("db.result.membership_tier"), tier);
            return tier;
        } catch (Exception e) {
            span.recordException(e);
            span.setAttribute(AttributeKey.booleanKey("error"), true);
            return "REGULAR";
        } finally {
            span.end();
        }
    }
}
