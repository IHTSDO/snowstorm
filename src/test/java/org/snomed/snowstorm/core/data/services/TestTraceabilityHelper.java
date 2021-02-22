package org.snomed.snowstorm.core.data.services;

import org.snomed.snowstorm.core.data.services.traceability.Activity;

import java.util.Stack;

public class TestTraceabilityHelper {

	public static Activity getActivity(Stack<Activity> activitiesLogged) throws InterruptedException {
		return getActivityWithTimeout(20, activitiesLogged);
	}
	public static Activity getActivityWithTimeout(int maxWait, Stack<Activity> activitiesLogged) throws InterruptedException {
		int waited = 0;
		while (activitiesLogged.isEmpty() && waited < maxWait) {
			Thread.sleep(1_000);
			waited++;
		}
		if (activitiesLogged.isEmpty()) {
			return null;
		}
		return activitiesLogged.pop();
	}

}
