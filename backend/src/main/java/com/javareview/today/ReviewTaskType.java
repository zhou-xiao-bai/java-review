package com.javareview.today;

public enum ReviewTaskType {
	CARRY_OVER("carry_over", "顺延未完成"),
	DUE("due", "今日到期"),
	NEW("new", "新拓展"),
	MANUAL("manual", "今日加练");

	private final String apiValue;
	private final String label;

	ReviewTaskType(String apiValue, String label) {
		this.apiValue = apiValue;
		this.label = label;
	}

	public String apiValue() {
		return apiValue;
	}

	public String label() {
		return label;
	}
}
