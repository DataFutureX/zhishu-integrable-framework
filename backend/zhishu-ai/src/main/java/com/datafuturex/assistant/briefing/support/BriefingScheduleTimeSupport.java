package com.datafuturex.assistant.briefing.support;

import org.springframework.util.StringUtils;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 将 DAILY/WEEKLY/CRON 调度编译为下次执行时间（不依赖 Quartz 解析器）。
 */
public final class BriefingScheduleTimeSupport {

    private BriefingScheduleTimeSupport() {
    }

    /**
     * Spring 6 段 cron 字符串（秒 分 时 日 月 周），主要用于展示/落库。
     */
    public static String compileCron(String scheduleType, String scheduleTime, String scheduleDays, String cronExpr) {
        String type = normalizeType(scheduleType);
        return switch (type) {
            case "CRON" -> StringUtils.hasText(cronExpr) ? cronExpr.trim() : null;
            case "WEEKLY" -> {
                LocalTime t = parseTime(scheduleTime);
                String days = toCronDays(scheduleDays);
                yield String.format(Locale.ROOT, "0 %d %d ? * %s", t.getMinute(), t.getHour(), days);
            }
            default -> {
                LocalTime t = parseTime(scheduleTime);
                yield String.format(Locale.ROOT, "0 %d %d * * ?", t.getMinute(), t.getHour());
            }
        };
    }

    public static LocalDateTime computeNextRunAt(
            String scheduleType,
            String scheduleTime,
            String scheduleDays,
            String cronExpr,
            String timezone,
            LocalDateTime from) {
        ZoneId zone = resolveZone(timezone);
        ZonedDateTime base = (from != null ? from : LocalDateTime.now())
                .atZone(ZoneId.systemDefault())
                .withZoneSameInstant(zone);
        String type = normalizeType(scheduleType);
        return switch (type) {
            case "WEEKLY" -> nextWeekly(base, parseTime(scheduleTime), parseDays(scheduleDays)).toLocalDateTime();
            case "CRON" -> nextFromSimpleCron(base, cronExpr).toLocalDateTime();
            default -> nextDaily(base, parseTime(scheduleTime)).toLocalDateTime();
        };
    }

    public static ZoneId resolveZone(String timezone) {
        if (!StringUtils.hasText(timezone)) {
            return ZoneId.of("Asia/Shanghai");
        }
        try {
            return ZoneId.of(timezone.trim());
        } catch (Exception e) {
            return ZoneId.of("Asia/Shanghai");
        }
    }

    private static String normalizeType(String scheduleType) {
        if (!StringUtils.hasText(scheduleType)) {
            return "DAILY";
        }
        return scheduleType.trim().toUpperCase(Locale.ROOT);
    }

    private static LocalTime parseTime(String scheduleTime) {
        if (!StringUtils.hasText(scheduleTime)) {
            return LocalTime.of(8, 0);
        }
        String raw = scheduleTime.trim();
        try {
            return LocalTime.parse(raw.length() == 5 ? raw : raw.substring(0, Math.min(5, raw.length())));
        } catch (Exception e) {
            return LocalTime.of(8, 0);
        }
    }

    private static ZonedDateTime nextDaily(ZonedDateTime base, LocalTime time) {
        ZonedDateTime candidate = base.toLocalDate().atTime(time).atZone(base.getZone());
        if (!candidate.isAfter(base)) {
            candidate = candidate.plusDays(1);
        }
        return candidate;
    }

    private static ZonedDateTime nextWeekly(ZonedDateTime base, LocalTime time, Set<Integer> days) {
        Set<Integer> use = days.isEmpty() ? Set.of(DayOfWeek.MONDAY.getValue()) : days;
        for (int i = 0; i < 14; i++) {
            LocalDate date = base.toLocalDate().plusDays(i);
            if (!use.contains(date.getDayOfWeek().getValue())) {
                continue;
            }
            ZonedDateTime candidate = date.atTime(time).atZone(base.getZone());
            if (candidate.isAfter(base)) {
                return candidate;
            }
        }
        return base.plusDays(7).toLocalDate().atTime(time).atZone(base.getZone());
    }

    /**
     * 仅支持常见「0 m H * * ?」或「0 m H ? * DOW」形态；无法解析时回退到次日同刻。
     */
    private static ZonedDateTime nextFromSimpleCron(ZonedDateTime base, String cronExpr) {
        if (!StringUtils.hasText(cronExpr)) {
            return nextDaily(base, LocalTime.of(8, 0));
        }
        String[] parts = cronExpr.trim().split("\\s+");
        if (parts.length < 6) {
            return nextDaily(base, LocalTime.of(8, 0));
        }
        try {
            int minute = Integer.parseInt(parts[1]);
            int hour = Integer.parseInt(parts[2]);
            LocalTime time = LocalTime.of(hour, minute);
            String dow = parts[5];
            if ("?".equals(dow) || "*".equals(dow)) {
                return nextDaily(base, time);
            }
            Set<Integer> days = Arrays.stream(dow.split(","))
                    .map(String::trim)
                    .filter(s -> !s.isEmpty())
                    .map(BriefingScheduleTimeSupport::cronDowToIso)
                    .collect(Collectors.toCollection(HashSet::new));
            return nextWeekly(base, time, days);
        } catch (Exception e) {
            return nextDaily(base, LocalTime.of(8, 0));
        }
    }

    private static Set<Integer> parseDays(String scheduleDays) {
        if (!StringUtils.hasText(scheduleDays)) {
            return Set.of();
        }
        return Arrays.stream(scheduleDays.split("[,;\\s]+"))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .map(Integer::parseInt)
                .filter(d -> d >= 1 && d <= 7)
                .collect(Collectors.toCollection(HashSet::new));
    }

    private static String toCronDays(String scheduleDays) {
        Set<Integer> days = parseDays(scheduleDays);
        if (days.isEmpty()) {
            return "MON";
        }
        return days.stream()
                .sorted()
                .map(BriefingScheduleTimeSupport::isoToCronDow)
                .collect(Collectors.joining(","));
    }

    private static int cronDowToIso(String token) {
        return switch (token.toUpperCase(Locale.ROOT)) {
            case "SUN", "1" -> 7;
            case "MON", "2" -> 1;
            case "TUE", "3" -> 2;
            case "WED", "4" -> 3;
            case "THU", "5" -> 4;
            case "FRI", "6" -> 5;
            case "SAT", "7" -> 6;
            default -> Integer.parseInt(token);
        };
    }

    private static String isoToCronDow(int iso) {
        return switch (iso) {
            case 1 -> "MON";
            case 2 -> "TUE";
            case 3 -> "WED";
            case 4 -> "THU";
            case 5 -> "FRI";
            case 6 -> "SAT";
            default -> "SUN";
        };
    }
}
