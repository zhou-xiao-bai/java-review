package com.javareview.llm;

public record LlmResult(boolean success, String content, String errorMessage, boolean fallbackUsed) {

	public static LlmResult success(String content) {
		return new LlmResult(true, content, null, false);
	}

	public static LlmResult failure(String errorMessage) {
		return new LlmResult(false, null, errorMessage, false);
	}

	public static LlmResult fallback(String content, String errorMessage) {
		return new LlmResult(true, content, errorMessage, true);
	}
}
