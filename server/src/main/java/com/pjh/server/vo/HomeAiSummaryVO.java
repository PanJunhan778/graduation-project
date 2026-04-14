package com.pjh.server.vo;

import lombok.Data;

import java.util.List;

@Data
public class HomeAiSummaryVO {

    private List<String> summaryLines;

    private String generatedAt;
}
