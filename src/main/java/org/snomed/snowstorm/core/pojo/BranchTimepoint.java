package org.snomed.snowstorm.core.pojo;

import com.google.common.base.Strings;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.regex.Pattern;

public class BranchTimepoint {

	public static final String DATE_FORMAT_STRING = "yyyy-MM-ddTHH:mm:ss.SSSZ";
	public static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat(DATE_FORMAT_STRING.replace("T", "'T'").replace("Z", "'Z'"));
	public static final Pattern EPOCH_FORMAT_MATCHER = Pattern.compile("\\d+");
	public static final String BRANCH_CREATION_TIMEPOINT = "-";
	public static final String BRANCH_BASE_TIMEPOINT = "^";

	private String branchPath;
	private boolean branchCreationTimepoint;
	private boolean branchBaseTimepoint;
	private Date timepoint;

	public BranchTimepoint() {}

	public BranchTimepoint(String branchPath) {
		this.branchPath = branchPath;
	}

	public BranchTimepoint(String branchPath, String timepointString) {
		this(branchPath);
		if (!Strings.isNullOrEmpty(timepointString)) {
			if (timepointString.equals(BRANCH_CREATION_TIMEPOINT)) {
				branchCreationTimepoint = true;
			} else if (timepointString.equals(BRANCH_BASE_TIMEPOINT)) {
				branchBaseTimepoint = true;
			} else if (EPOCH_FORMAT_MATCHER.matcher(timepointString).matches()) {
				try {
					timepoint = new Date(Long.parseLong(timepointString));
				} catch (NumberFormatException e) {
					throwMalformedException();
				}
			} else if (timepointString.length() == DATE_FORMAT_STRING.length()) {
				try {
					timepoint = DATE_FORMAT.parse(timepointString);
				} catch (ParseException e) {
					throwMalformedException();
				}
			} else {
				throwMalformedException();
			}
		}
	}

	private void throwMalformedException() {
		throw new IllegalArgumentException("Malformed branch timepoint, please use format '" + DATE_FORMAT_STRING + "', epoch milliseconds or '" + BRANCH_CREATION_TIMEPOINT + "'.");
	}

	public String getBranchPath() {
		return branchPath;
	}

	public boolean isBranchCreationTimepoint() {
		return branchCreationTimepoint;
	}

	public boolean isBranchBaseTimepoint() {
		return branchBaseTimepoint;
	}

	public Date getTimepoint() {
		return timepoint;
	}

	@Override
	public String toString() {
		return "BranchTimepoint{" +
				"branchPath='" + branchPath + '\'' +
				", branchCreationTimepoint=" + branchCreationTimepoint +
				", timepoint=" + (timepoint == null ? null : DATE_FORMAT.format(timepoint)) +
				'}';
	}
}
