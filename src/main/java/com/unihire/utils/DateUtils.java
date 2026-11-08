package com.unihire.utils;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class DateUtils {

    private DateUtils() {}

    private static final String[] MONTH_NAMES = {
            "Jan", "Feb", "Mar", "Apr", "May", "Jun",
            "Jul", "Aug", "Sept", "Oct", "Nov", "Dec"
    };

    private static final Map<String, String> MONTH_TEXT_MAP = Map.ofEntries(
            Map.entry("jan", "Jan"), Map.entry("january", "Jan"),
            Map.entry("feb", "Feb"), Map.entry("february", "Feb"),
            Map.entry("mar", "Mar"), Map.entry("march", "Mar"),
            Map.entry("apr", "Apr"), Map.entry("april", "Apr"),
            Map.entry("may", "May"),
            Map.entry("jun", "Jun"), Map.entry("june", "Jun"),
            Map.entry("jul", "Jul"), Map.entry("july", "Jul"),
            Map.entry("aug", "Aug"), Map.entry("august", "Aug"),
            Map.entry("sep", "Sept"), Map.entry("sept", "Sept"), Map.entry("september", "Sept"),
            Map.entry("oct", "Oct"), Map.entry("october", "Oct"),
            Map.entry("nov", "Nov"), Map.entry("november", "Nov"),
            Map.entry("dec", "Dec"), Map.entry("december", "Dec")
    );

    private static final Pattern ISO_DATE_PATTERN = Pattern.compile("^(\\d{4})[-/](\\d{1,2})[-/]\\d{1,2}$");
    private static final Pattern YEAR_MONTH_PATTERN = Pattern.compile("^(\\d{4})[-/](\\d{1,2})$");
    private static final Pattern MONTH_YEAR_NUMERIC_PATTERN = Pattern.compile("^(\\d{1,2})[-/](\\d{4})$");
    private static final Pattern DAY_MONTH_YEAR_PATTERN = Pattern.compile("^\\d{1,2}[-/](\\d{1,2})[-/](\\d{4})$");
    private static final Pattern FOUR_DIGIT_YEAR_PATTERN = Pattern.compile("\\b(19\\d{2}|20\\d{2})\\b");

    /**
     * Formats an input date string into Month-Year format (e.g., "Sept-2026", "May-2023").
     * Omits any day value. If the string represents present/ongoing, returns "Present".
     *
     * @param dateStr the input date string
     * @return formatted date string without day, or null if input is null/blank
     */
    public static String formatMonthYear(String dateStr) {
        if (dateStr == null || dateStr.trim().isEmpty()) {
            return null;
        }

        String trimmed = dateStr.trim();
        if (trimmed.equalsIgnoreCase("present") || trimmed.equalsIgnoreCase("current")) {
            return "Present";
        }

        // 1. Check YYYY-MM-DD or YYYY/MM/DD (e.g. "2023-05-01")
        Matcher isoMatcher = ISO_DATE_PATTERN.matcher(trimmed);
        if (isoMatcher.matches()) {
            String year = isoMatcher.group(1);
            int month = Integer.parseInt(isoMatcher.group(2));
            if (month >= 1 && month <= 12) {
                return MONTH_NAMES[month - 1] + "-" + year;
            }
        }

        // 2. Check YYYY-MM or YYYY/MM (e.g. "2026-09")
        Matcher ymMatcher = YEAR_MONTH_PATTERN.matcher(trimmed);
        if (ymMatcher.matches()) {
            String year = ymMatcher.group(1);
            int month = Integer.parseInt(ymMatcher.group(2));
            if (month >= 1 && month <= 12) {
                return MONTH_NAMES[month - 1] + "-" + year;
            }
        }

        // 3. Check MM-YYYY or MM/YYYY (e.g. "09-2026")
        Matcher myMatcher = MONTH_YEAR_NUMERIC_PATTERN.matcher(trimmed);
        if (myMatcher.matches()) {
            int month = Integer.parseInt(myMatcher.group(1));
            String year = myMatcher.group(2);
            if (month >= 1 && month <= 12) {
                return MONTH_NAMES[month - 1] + "-" + year;
            }
        }

        // 4. Check DD-MM-YYYY or DD/MM/YYYY (e.g. "01-05-2023")
        Matcher dmyMatcher = DAY_MONTH_YEAR_PATTERN.matcher(trimmed);
        if (dmyMatcher.matches()) {
            int month = Integer.parseInt(dmyMatcher.group(1));
            String year = dmyMatcher.group(2);
            if (month >= 1 && month <= 12) {
                return MONTH_NAMES[month - 1] + "-" + year;
            }
        }

        // 5. Check text month and year (e.g. "Sept 2026", "September 2026", "May 2023", "01 May 2023")
        Matcher yearMatcher = FOUR_DIGIT_YEAR_PATTERN.matcher(trimmed);
        if (yearMatcher.find()) {
            String year = yearMatcher.group(1);
            String[] tokens = trimmed.toLowerCase().split("[\\s,\\-_/]+");
            for (String token : tokens) {
                if (MONTH_TEXT_MAP.containsKey(token)) {
                    return MONTH_TEXT_MAP.get(token) + "-" + year;
                }
            }
            if (trimmed.matches("^\\d{4}$")) {
                return trimmed;
            }
        }

        return trimmed;
    }

    /**
     * Formats a date range into Month-Year format, e.g. "Sept-2026 - Present" or "May-2023 - Dec-2025".
     *
     * @param startDate the start date
     * @param endDate   the end date (null/empty/present defaults to "Present")
     * @return formatted date range string
     */
    public static String formatDateRange(String startDate, String endDate) {
        String start = formatMonthYear(startDate);
        boolean isPresent = endDate == null || endDate.trim().isEmpty()
                || endDate.equalsIgnoreCase("present") || endDate.equalsIgnoreCase("current");
        String end = isPresent ? "Present" : formatMonthYear(endDate);

        if (start != null && !start.isEmpty()) {
            return start + " - " + end;
        }
        return end != null ? end : "";
    }
}
