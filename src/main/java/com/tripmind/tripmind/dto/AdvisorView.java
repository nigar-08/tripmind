package com.tripmind.tripmind.dto;

import java.util.List;

public record AdvisorView(
        String verdict,
        String summary,
        List<String> recommendations,
        List<AlternativeView> alternatives,
        String proactiveTip,
        String visaNote,
        List<String> nextSteps,
        String delightNote
) implements java.io.Serializable {
}
