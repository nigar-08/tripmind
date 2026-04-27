package com.tripmind.tripmind.dto;

public record BookingLinkView(
        String provider,
        String label,
        String url,
        String note,
        Boolean enabled
) implements java.io.Serializable {
}
