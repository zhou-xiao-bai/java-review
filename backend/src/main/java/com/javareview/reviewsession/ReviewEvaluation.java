package com.javareview.reviewsession;

import java.math.BigDecimal;
import java.util.List;

public record ReviewEvaluation(
		String overallComment,
		List<String> correctPoints,
		List<String> missingPoints,
		List<String> inaccuratePoints,
		String referenceAnswer,
		ReviewScore score,
		List<String> weakPoints,
		String nextProbe,
		String nextStatus) {

	public record ReviewScore(
			BigDecimal conclusionAccuracy,
			BigDecimal mechanismExplanation,
			BigDecimal boundaryCases,
			BigDecimal transferApplication,
			BigDecimal overall) {
	}
}
