package com.javareview.settings;

import java.util.Arrays;

public enum ReviewedPointSchedulingPolicy {
	FOLLOW_SCOPE("follow_scope"),
	KEEP_REVIEWED("keep_reviewed");

	private final String apiValue;

	ReviewedPointSchedulingPolicy(String apiValue) {
		this.apiValue = apiValue;
	}

	public String apiValue() {
		return apiValue;
	}

	public static ReviewedPointSchedulingPolicy fromApiValue(String value) {
		return Arrays.stream(values())
				.filter(policy -> policy.apiValue.equals(value))
				.findFirst()
				.orElseThrow(() -> new IllegalArgumentException("Invalid reviewedPointSchedulingPolicy."));
	}
}
