package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.OpportunityDomainException;
import io.github.archetom.crm.domain.valueobject.ProductId;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Immutable product snapshot and server-calculated amount owned by an opportunity. */
public record OpportunityProductLine(
        ProductId productId,
        String productName,
        String productCode,
        String categoryName,
        String unit,
        BigDecimal listPrice,
        BigDecimal unitPrice,
        BigDecimal quantity,
        BigDecimal discountRate,
        BigDecimal lineAmount,
        int displayOrder) {

    public OpportunityProductLine {
        if (productId == null || displayOrder < 0) {
            throw new OpportunityDomainException("opportunity product line is invalid");
        }
        productName = required(productName, 200, "product name");
        productCode = required(productCode, 100, "product code");
        categoryName = optional(categoryName, 200, "product category");
        unit = optional(unit, 50, "product unit");
        listPrice = price(listPrice, "product list price");
        unitPrice = price(unitPrice, "product unit price");
        quantity = quantity(quantity);
        discountRate = rate(discountRate);
        BigDecimal calculated = unitPrice.multiply(quantity)
                .setScale(2, RoundingMode.HALF_UP);
        lineAmount = amount(lineAmount, "product line amount");
        if (calculated.compareTo(lineAmount) != 0) {
            throw new OpportunityDomainException(
                    "opportunity product line amount is inconsistent");
        }
    }

    public static OpportunityProductLine snapshot(
            Product product, String categoryName, BigDecimal quantity,
            BigDecimal unitPrice, int displayOrder) {
        if (product == null || !product.isAvailable()) {
            throw new OpportunityDomainException(
                    "only available products can be added to an opportunity");
        }
        BigDecimal price = unitPrice == null ? product.getListPrice() : unitPrice;
        BigDecimal normalizedPrice = price(price, "product unit price");
        BigDecimal normalizedQuantity = quantity(quantity);
        BigDecimal amount = normalizedPrice.multiply(normalizedQuantity)
                .setScale(2, RoundingMode.HALF_UP);
        BigDecimal rate = product.getListPrice().signum() == 0
                ? BigDecimal.ZERO.setScale(4)
                : product.getListPrice().subtract(normalizedPrice)
                    .max(BigDecimal.ZERO).multiply(new BigDecimal("100"))
                    .divide(product.getListPrice(), 4, RoundingMode.HALF_UP)
                    .min(new BigDecimal("100.0000"));
        return new OpportunityProductLine(product.getId(), product.getName(),
                product.getCode(), categoryName, product.getUnit(),
                product.getListPrice(), normalizedPrice, normalizedQuantity,
                rate, amount, displayOrder);
    }

    private static BigDecimal price(BigDecimal value, String description) {
        return decimal(value, 18, description);
    }

    private static BigDecimal amount(BigDecimal value, String description) {
        return decimal(value, 22, description);
    }

    private static BigDecimal decimal(
            BigDecimal value, int precision, String description) {
        if (value == null || value.signum() < 0) {
            throw new OpportunityDomainException(description + " is invalid");
        }
        try {
            BigDecimal normalized = value.setScale(2);
            if (normalized.precision() > precision) {
                throw new OpportunityDomainException(description + " is invalid");
            }
            return normalized;
        } catch (ArithmeticException exception) {
            throw new OpportunityDomainException(
                    description + " supports at most two decimal places");
        }
    }

    private static BigDecimal quantity(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new OpportunityDomainException("product quantity is invalid");
        }
        try {
            BigDecimal normalized = value.setScale(4);
            if (normalized.precision() > 18) {
                throw new OpportunityDomainException("product quantity is invalid");
            }
            return normalized;
        } catch (ArithmeticException exception) {
            throw new OpportunityDomainException(
                    "product quantity supports at most four decimal places");
        }
    }

    private static BigDecimal rate(BigDecimal value) {
        if (value == null || value.signum() < 0
                || value.compareTo(new BigDecimal("100")) > 0) {
            throw new OpportunityDomainException("product discount rate is invalid");
        }
        try {
            return value.setScale(4);
        } catch (ArithmeticException exception) {
            throw new OpportunityDomainException("product discount rate is invalid");
        }
    }

    private static String required(String value, int max, String description) {
        String normalized = optional(value, max, description);
        if (normalized == null) {
            throw new OpportunityDomainException(description + " is required");
        }
        return normalized;
    }

    private static String optional(String value, int max, String description) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw new OpportunityDomainException(description + " is too long");
        }
        return normalized;
    }
}
