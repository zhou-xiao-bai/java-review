package com.javareview.reviewpoint;

import java.util.List;

public record MasteryCard(
		String oneSentence,
		List<String> answerSkeleton,
		List<String> mustRemember,
		String nextProbe) {
}
