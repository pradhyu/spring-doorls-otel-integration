package com.example.droolsotel.model;

import com.example.droolsotel.otel.TelemetrySafe;
import java.util.ArrayList;
import java.util.List;

public class CustomerFact {
    @TelemetrySafe
    private String memberId;
    private String name;
    private int age;
    @TelemetrySafe
    private String membershipTier;
    private double purchaseAmount;
    @TelemetrySafe
    private double discountPercentage;
    @TelemetrySafe
    private double finalAmount;
    private List<String> appliedRules = new ArrayList<>();

    public CustomerFact() {}

    public CustomerFact(String name, int age, String membershipTier, double purchaseAmount) {
        this.name = name;
        this.age = age;
        this.membershipTier = membershipTier;
        this.purchaseAmount = purchaseAmount;
        this.finalAmount = purchaseAmount;
        this.discountPercentage = 0.0;
    }

    public CustomerFact(String memberId, String name, int age, String membershipTier, double purchaseAmount) {
        this.memberId = memberId;
        this.name = name;
        this.age = age;
        this.membershipTier = membershipTier;
        this.purchaseAmount = purchaseAmount;
        this.finalAmount = purchaseAmount;
        this.discountPercentage = 0.0;
    }

    public String getMemberId() {
        return memberId;
    }

    public void setMemberId(String memberId) {
        this.memberId = memberId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public int getAge() {
        return age;
    }

    public void setAge(int age) {
        this.age = age;
    }

    public String getMembershipTier() {
        return membershipTier;
    }

    public void setMembershipTier(String membershipTier) {
        this.membershipTier = membershipTier;
    }

    public double getPurchaseAmount() {
        return purchaseAmount;
    }

    public void setPurchaseAmount(double purchaseAmount) {
        this.purchaseAmount = purchaseAmount;
        recalculateFinalAmount();
    }

    public double getDiscountPercentage() {
        return discountPercentage;
    }

    public void setDiscountPercentage(double discountPercentage) {
        this.discountPercentage = discountPercentage;
        recalculateFinalAmount();
    }

    public void addDiscount(double additionalDiscount) {
        this.discountPercentage += additionalDiscount;
        recalculateFinalAmount();
    }

    public double getFinalAmount() {
        return finalAmount;
    }

    public void setFinalAmount(double finalAmount) {
        this.finalAmount = finalAmount;
    }

    public List<String> getAppliedRules() {
        return appliedRules;
    }

    public void setAppliedRules(List<String> appliedRules) {
        this.appliedRules = appliedRules;
    }

    public void addAppliedRule(String ruleName) {
        if (!this.appliedRules.contains(ruleName)) {
            this.appliedRules.add(ruleName);
        }
    }

    private void recalculateFinalAmount() {
        this.finalAmount = Math.max(0.0, this.purchaseAmount * (1.0 - (this.discountPercentage / 100.0)));
    }

    @Override
    public String toString() {
        return "CustomerFact{" +
                "memberId='" + memberId + '\'' +
                ", name='" + name + '\'' +
                ", age=" + age +
                ", membershipTier='" + membershipTier + '\'' +
                ", purchaseAmount=" + purchaseAmount +
                ", discountPercentage=" + discountPercentage +
                ", finalAmount=" + finalAmount +
                ", appliedRules=" + appliedRules +
                '}';
    }
}
