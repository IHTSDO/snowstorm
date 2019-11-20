package org.snomed.snowstorm.core.util;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.GregorianCalendar;

public class DateUtil {

	public static final SimpleDateFormat DATE_STAMP_FORMAT = new SimpleDateFormat("yyyyMMdd");

	/**
	 * @param field the calendar field.
	 * @param amount the amount of date or time to be added to the field.
	 * @return Date relevant to now plus the amount of time specified.
	 */
	public static Date newDatePlus(int field, int amount) {
		GregorianCalendar remoteClassificationCutoff = new GregorianCalendar();
		remoteClassificationCutoff.add(field, amount);
		return remoteClassificationCutoff.getTime();
	}

	public static int getTodaysEffectiveTime() {
		return Integer.parseInt(DateUtil.DATE_STAMP_FORMAT.format(new Date()));
	}
}
