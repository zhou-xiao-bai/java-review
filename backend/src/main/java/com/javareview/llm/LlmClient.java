package com.javareview.llm;

import com.javareview.settings.SettingsDtos.LlmTestResponse;
import com.javareview.settings.UserSettings;

public interface LlmClient {

	LlmResult complete(UserSettings settings, String systemPrompt, String userPrompt);

	LlmTestResponse test(UserSettings settings);
}
