package com.tripmind.tripmind.dto;

public record TripRiskView(
        String severity,
        String code,
        String message
) implements java.io.Serializable {
}
