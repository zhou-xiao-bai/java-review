package com.javareview.reviewsession;

import java.math.BigDecimal;
import java.util.List;

import com.javareview.reviewpoint.MasteryCard;

public record ReviewEvaluation(
		String overallComment,
		List<String> correctPoints,
		List<String> missingPoints,
		List<String> inaccuratePoints,
		String referenceAnswer,
		ReviewScore score,
		List<WeaknessSignal> weakSignals,
		List<String> weakPoints,
		String nextProbe,
		String nextStatus,
		MasteryCard masteryCard) {

	public record ReviewScore(
			BigDecimal conclusionAccuracy,
			BigDecimal mechanismExplanation,
			BigDecimal boundaryCases,
			BigDecimal transferApplication,
			BigDecimal overall) {
	}

	public record WeaknessSignal(
			String category,
			String label,
			String evidence,
			int severity) {
	}
}
