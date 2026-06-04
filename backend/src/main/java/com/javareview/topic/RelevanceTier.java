package com.javareview.topic;

public enum RelevanceTier {
	CORE,
	PROJECT,
	SUPPLEMENT,
	ARCHIVED;

	public boolean autoPlannable() {
		return this == CORE || this == PROJECT;
	}
}
