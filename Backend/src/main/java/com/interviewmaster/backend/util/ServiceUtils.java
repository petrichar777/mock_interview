package com.interviewmaster.backend.util;

import com.interviewmaster.backend.exception.BusinessException;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ServiceUtils {

    private ServiceUtils() {}

    // ── String splitting ──

    public static List<String> splitExplanations(String analysis) {
        List<String> list = new ArrayList<>();
        String text = analysis == null ? "" : analysis.trim();
        if (text.isEmpty()) {
            return list;
        }
        String normalized = text.replace("；", "。").replace(";", "。");
        String[] parts = normalized.split("。+");
        for (String part : parts) {
            String value = part == null ? "" : part.trim();
            if (!value.isEmpty()) {
                list.add(value);
            }
        }
        if (list.isEmpty()) {
            list.add(text);
        }
        return list;
    }

    public static List<String> splitTags(String tags) {
        List<String> list = new ArrayList<>();
        String text = tags == null ? "" : tags.trim();
        if (text.isEmpty()) {
            return list;
        }
        String[] parts = text.replace("，", ",").split(",");
        for (String part : parts) {
            String value = part == null ? "" : part.trim();
            if (!value.isEmpty()) {
                list.add(value);
            }
        }
        return list;
    }

    public static List<String> splitAnswers(String answers) {
        List<String> list = new ArrayList<>();
        String text = answers == null ? "" : answers.trim();
        if (text.isEmpty()) {
            return list;
        }
        String[] parts = text.replace("，", ",").split(",");
        for (String part : parts) {
            String value = part == null ? "" : part.trim();
            if (!value.isEmpty()) {
                list.add(value.toUpperCase());
            }
        }
        return list;
    }

    // ── Safe number conversions (nullable input) ──

    /**
     * Returns null for null/unparseable, Long for valid numbers.
     */
    public static Long toLongOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.parseLong(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Returns 0L for null/unparseable/negative, long for valid numbers.
     */
    public static long toLongOrZero(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number number) {
            long v = number.longValue();
            return v < 0 ? 0L : v;
        }
        try {
            long v = Long.parseLong(value.toString());
            return v < 0 ? 0L : v;
        } catch (NumberFormatException ex) {
            return 0L;
        }
    }

    public static Integer toIntegerOrNull(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        try {
            return Integer.parseInt(value.toString());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    public static int toIntOrDefault(Object value, int defaultValue) {
        Integer result = toIntegerOrNull(value);
        return result != null ? result : defaultValue;
    }

    public static LocalDateTime toLocalDateTime(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        String text = value.toString().trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return LocalDateTime.parse(text);
        } catch (Exception ignored) {
        }
        try {
            return LocalDateTime.parse(text.replace(" ", "T"));
        } catch (Exception ignored) {
        }
        return null;
    }

    // ── Clamp helpers ──

    public static int safeInt(Long value) {
        if (value == null || value <= 0) {
            return 0;
        }
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return value.intValue();
    }

    public static int safeInt(long value) {
        if (value < 0) {
            return 0;
        }
        if (value > Integer.MAX_VALUE) {
            return Integer.MAX_VALUE;
        }
        return (int) value;
    }

    public static long safeLong(Long value) {
        if (value == null || value <= 0) {
            return 0L;
        }
        return value;
    }

    // ── Pagination (strict — throws on invalid) ──

    public static int requirePage(Integer page) {
        if (page == null || page < 1) {
            throw new BusinessException("参数错误：page 必须大于等于 1");
        }
        return page;
    }

    public static int requirePageSize(Integer size) {
        if (size == null || size < 1) {
            throw new BusinessException("参数错误：size 必须大于等于 1");
        }
        if (size > 100) {
            throw new BusinessException("参数错误：size 不能超过 100");
        }
        return size;
    }

    // ── Pagination (lenient — defaults on missing) ──

    public static int pageOrDefault(Integer page) {
        if (page == null || page < 1) {
            return 1;
        }
        return page;
    }

    public static int pageSizeOrDefault(Integer size) {
        if (size == null || size < 1) {
            return 10;
        }
        return Math.min(size, 50);
    }

    // ── Calendar helpers ──

    public static int calcConsecutiveDays(java.util.Set<LocalDate> dateSet) {
        if (dateSet == null || dateSet.isEmpty()) {
            return 0;
        }
        int days = 0;
        LocalDate cursor = LocalDate.now();
        while (dateSet.contains(cursor)) {
            days++;
            cursor = cursor.minusDays(1);
        }
        return days;
    }
}
