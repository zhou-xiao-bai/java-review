package com.javareview.reviewpoint;

public enum AutoPlanTier {

	CORE,
	EXPAND,
	OPTIONAL;

	public boolean autoExpandable() {
		return this == CORE || this == EXPAND;
	}
}
