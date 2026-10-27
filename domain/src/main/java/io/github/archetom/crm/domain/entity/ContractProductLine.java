package io.github.archetom.crm.domain.entity;

import io.github.archetom.crm.domain.exception.ContractDomainException;
import io.github.archetom.crm.domain.valueobject.ProductId;

import java.math.BigDecimal;
import java.math.RoundingMode;

public record ContractProductLine(
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

    public ContractProductLine {
        if (productId == null || displayOrder < 0) {
            throw new ContractDomainException("contract product line is invalid");
        }
        productName = required(productName, 200, "product name");
        productCode = required(productCode, 100, "product code");
        categoryName = optional(categoryName, 200, "product category");
        unit = optional(unit, 50, "product unit");
        listPrice = money(listPrice, 18, "product list price");
        unitPrice = money(unitPrice, 18, "product unit price");
        quantity = quantity(quantity);
        discountRate = rate(discountRate);
        lineAmount = money(lineAmount, 22, "product line amount");
        BigDecimal calculated = unitPrice.multiply(quantity).setScale(2, RoundingMode.HALF_UP);
        if (calculated.compareTo(lineAmount) != 0) {
            throw new ContractDomainException("contract product line amount is inconsistent");
        }
    }

    public static ContractProductLine snapshot(
            Product product, String categoryName, BigDecimal quantity,
            BigDecimal unitPrice, int displayOrder) {
        if (product == null || !product.isAvailable()) {
            throw new ContractDomainException("only available products can be added to a contract");
        }
        BigDecimal price = money(unitPrice == null ? product.getListPrice() : unitPrice,
                18, "product unit price");
        BigDecimal normalizedQuantity = quantity(quantity);
        BigDecimal amount = price.multiply(normalizedQuantity).setScale(2, RoundingMode.HALF_UP);
        BigDecimal rate = product.getListPrice().signum() == 0
                ? BigDecimal.ZERO.setScale(4)
                : product.getListPrice().subtract(price).max(BigDecimal.ZERO)
                .multiply(new BigDecimal("100"))
                .divide(product.getListPrice(), 4, RoundingMode.HALF_UP)
                .min(new BigDecimal("100.0000"));
        return new ContractProductLine(product.getId(), product.getName(), product.getCode(),
                categoryName, product.getUnit(), product.getListPrice(), price,
                normalizedQuantity, rate, amount, displayOrder);
    }

    private static BigDecimal money(BigDecimal value, int precision, String description) {
        if (value == null || value.signum() < 0) {
            throw new ContractDomainException(description + " is invalid");
        }
        try {
            BigDecimal normalized = value.setScale(2);
            if (normalized.precision() > precision) {
                throw new ContractDomainException(description + " is invalid");
            }
            return normalized;
        } catch (ArithmeticException exception) {
            throw new ContractDomainException(description + " supports at most two decimal places");
        }
    }

    private static BigDecimal quantity(BigDecimal value) {
        if (value == null || value.signum() <= 0) {
            throw new ContractDomainException("product quantity is invalid");
        }
        try {
            BigDecimal normalized = value.setScale(4);
            if (normalized.precision() > 18) {
                throw new ContractDomainException("product quantity is invalid");
            }
            return normalized;
        } catch (ArithmeticException exception) {
            throw new ContractDomainException("product quantity supports at most four decimal places");
        }
    }

    private static BigDecimal rate(BigDecimal value) {
        if (value == null || value.signum() < 0 || value.compareTo(new BigDecimal("100")) > 0) {
            throw new ContractDomainException("product discount rate is invalid");
        }
        try {
            return value.setScale(4);
        } catch (ArithmeticException exception) {
            throw new ContractDomainException("product discount rate is invalid");
        }
    }

    private static String required(String value, int max, String description) {
        String normalized = optional(value, max, description);
        if (normalized == null) {
            throw new ContractDomainException(description + " is required");
        }
        return normalized;
    }

    private static String optional(String value, int max, String description) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim();
        if (normalized.length() > max) {
            throw new ContractDomainException(description + " is too long");
        }
        return normalized;
    }
}
