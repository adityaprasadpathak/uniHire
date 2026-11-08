package com.unihire.utils;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class DateUtilsTest {

    @Test
    void testFormatMonthYearIsoDate() {
        assertEquals("May-2023", DateUtils.formatMonthYear("2023-05-01"));
        assertEquals("Sept-2026", DateUtils.formatMonthYear("2026-09-15"));
        assertEquals("Jan-2024", DateUtils.formatMonthYear("2024-01-31"));
        assertEquals("Dec-2022", DateUtils.formatMonthYear("2022-12-05"));
    }

    @Test
    void testFormatMonthYearYearMonth() {
        assertEquals("May-2023", DateUtils.formatMonthYear("2023-05"));
        assertEquals("Sept-2026", DateUtils.formatMonthYear("2026-09"));
        assertEquals("Sept-2026", DateUtils.formatMonthYear("09-2026"));
        assertEquals("May-2023", DateUtils.formatMonthYear("05/2023"));
    }

    @Test
    void testFormatMonthYearTextMonth() {
        assertEquals("Sept-2026", DateUtils.formatMonthYear("September 2026"));
        assertEquals("Sept-2026", DateUtils.formatMonthYear("Sept 2026"));
        assertEquals("Sept-2026", DateUtils.formatMonthYear("Sep 2026"));
        assertEquals("May-2023", DateUtils.formatMonthYear("May 2023"));
        assertEquals("May-2023", DateUtils.formatMonthYear("01 May 2023"));
    }

    @Test
    void testFormatDateRange() {
        assertEquals("May-2023 - Present", DateUtils.formatDateRange("2023-05-01", null));
        assertEquals("Sept-2026 - Present", DateUtils.formatDateRange("2026-09-01", "Present"));
        assertEquals("Sept-2026 - Present", DateUtils.formatDateRange("2026-09-01", "present"));
        assertEquals("May-2023 - Sept-2026", DateUtils.formatDateRange("2023-05-01", "2026-09-15"));
    }

    @Test
    void testEdgeCases() {
        assertNull(DateUtils.formatMonthYear(null));
        assertNull(DateUtils.formatMonthYear(""));
        assertEquals("Present", DateUtils.formatMonthYear("Present"));
        assertEquals("Present", DateUtils.formatMonthYear("present"));
        assertEquals("2024", DateUtils.formatMonthYear("2024"));
    }
}
