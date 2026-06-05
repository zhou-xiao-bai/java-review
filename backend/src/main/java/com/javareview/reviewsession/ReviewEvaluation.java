package com.javareview.reviewsession;

import java.math.BigDecimal;
import java.util.List;

import com.javareview.reviewpoint.MasteryCard;

public record ReviewEvaluation(
		String overallComment,
		List<String> correctPoints,
		List<String> missingPoints,
		List<String> inaccuratePoints,
		List<Correction> corrections,
		String referenceAnswer,
		ReviewScore score,
		List<WeaknessSignal> weakSignals,
		List<String> weakPoints,
		String nextProbe,
		String nextStatus,
		MasteryCard masteryCard) {

	public ReviewEvaluation {
		correctPoints = correctPoints == null ? List.of() : correctPoints;
		missingPoints = missingPoints == null ? List.of() : missingPoints;
		inaccuratePoints = inaccuratePoints == null ? List.of() : inaccuratePoints;
		corrections = corrections == null ? List.of() : corrections;
		weakSignals = weakSignals == null ? List.of() : weakSignals;
		weakPoints = weakPoints == null ? List.of() : weakPoints;
	}

	public ReviewEvaluation(
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
		this(
				overallComment,
				correctPoints,
				missingPoints,
				inaccuratePoints,
				List.of(),
				referenceAnswer,
				score,
				weakSignals,
				weakPoints,
				nextProbe,
				nextStatus,
				masteryCard);
	}

	public record ReviewScore(
			BigDecimal conclusionAccuracy,
			BigDecimal mechanismExplanation,
			BigDecimal boundaryCases,
			BigDecimal transferApplication,
			BigDecimal overall) {
	}

	public record Correction(
			String userIssue,
			String correctAnswer,
			String explanation) {
	}

	public record WeaknessSignal(
			String category,
			String label,
			String evidence,
			int severity) {
	}
}
