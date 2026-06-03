package com.javareview.project;

import java.math.BigDecimal;
import java.util.List;

public record ProjectEvaluation(
		String overallComment,
		ProjectScore score,
		List<String> weakPoints,
		List<String> suggestedTopics) {

	public record ProjectScore(
			BigDecimal businessExpression,
			BigDecimal technicalDesign,
			BigDecimal tradeoffDecision,
			BigDecimal metricsEvidence,
			BigDecimal troubleshootingLoop,
			BigDecimal interviewPressure,
			BigDecimal overall) {
	}
}
