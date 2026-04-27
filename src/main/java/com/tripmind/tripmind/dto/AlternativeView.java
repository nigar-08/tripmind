package com.tripmind.tripmind.dto;

public record AlternativeView(
        String destination,
        String reason,
        Integer estimatedBudget
) implements java.io.Serializable {
}
