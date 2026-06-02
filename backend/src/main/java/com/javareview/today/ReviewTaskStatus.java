package com.javareview.today;

public enum ReviewTaskStatus {
	PENDING("pending", "待复习"),
	IN_PROGRESS("in_progress", "进行中"),
	COMPLETED("completed", "已完成"),
	SKIPPED("skipped", "已跳过");

	private final String apiValue;
	private final String label;

	ReviewTaskStatus(String apiValue, String label) {
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
