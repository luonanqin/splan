package luonq.aggregate;

import bean.StockBarAgg;
import bean.Total;
import util.Constants;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * 从<strong>按日期升序</strong>的日线序列聚合周/月/季 K，直接产出 {@link StockBarAgg}（与 {@code stock_bar_agg} 一致）：
 * {@code barDate} 为<strong>周期锚点日</strong>（周=该 ISO 周在 {@code trade_calendar} 中<strong>首个交易日</strong>，无日历数据时回退 ISO 周一；
 * 月=当月 1 日；季=季首月 1 日），在周期未结束时也不随新增交易日变化，
 * 以便增量同步时 on duplicate key 更新同一条记录；{@code firstTradeDate} 为周期内首个交易日；{@code lastTradeDate} 为周期内最后一条日线日期；OHLC 用 {@link BigDecimal} 聚合。
 */
public final class PeriodOhlcvAggregator {

    /** ISO-8601：周一为一周第一天，周编号与 {@link WeekFields#ISO} 一致 */
    private static final WeekFields WEEK_FIELDS = WeekFields.ISO;

    /** 与 {@link luonq.service.StockBarAggService} 及表 {@code period_type} 一致 */
    private static final String PT_WEEK = "week";
    private static final String PT_MONTH = "month";
    private static final String PT_QUARTER = "quarter";

    private PeriodOhlcvAggregator() {}

    /**
     * 周线 {@code bar_date} 使用 ISO 周一（无 {@code trade_calendar} 时）。
     *
     * @see #aggregateWeek(List, Function)
     */
    public static List<StockBarAgg> aggregateWeek(List<Total> dailiesAsc) {
        return aggregateWeek(dailiesAsc, null);
    }

    /**
     * @param resolveWeekBarDate 由 ISO 周内任一日解析该周锚点 {@code bar_date}（yyyy-MM-dd）；为 null 时用 ISO 周一
     */
    public static List<StockBarAgg> aggregateWeek(
            List<Total> dailiesAsc, Function<LocalDate, String> resolveWeekBarDate) {
        return aggregateByWeekKey(dailiesAsc, PT_WEEK, resolveWeekBarDate);
    }

    public static List<StockBarAgg> aggregateMonth(List<Total> dailiesAsc) {
        return aggregateByMonthKey(dailiesAsc, PT_MONTH);
    }

    public static List<StockBarAgg> aggregateQuarter(List<Total> dailiesAsc) {
        return aggregateByQuarterKey(dailiesAsc, PT_QUARTER);
    }

    private static List<StockBarAgg> aggregateByWeekKey(
            List<Total> dailiesAsc, String periodType, Function<LocalDate, String> resolveWeekBarDate) {
        if (dailiesAsc.isEmpty()) {
            return new ArrayList<>();
        }
        List<StockBarAgg> out = new ArrayList<>();
        List<Total> bucket = new ArrayList<>();
        LocalDate first = LocalDate.parse(dailiesAsc.get(0).getDate(), Constants.DB_DATE_FORMATTER);
        int weekKey = first.get(WEEK_FIELDS.weekOfWeekBasedYear());
        int weekYear = first.get(WEEK_FIELDS.weekBasedYear());

        for (Total t : dailiesAsc) {
            LocalDate d = LocalDate.parse(t.getDate(), Constants.DB_DATE_FORMATTER);
            int wk = d.get(WEEK_FIELDS.weekOfWeekBasedYear());
            int wy = d.get(WEEK_FIELDS.weekBasedYear());
            if (wk != weekKey || wy != weekYear) {
                flushBucket(out, bucket, periodType, resolveWeekBarDate);
                bucket.clear();
                weekKey = wk;
                weekYear = wy;
            }
            bucket.add(t);
        }
        flushBucket(out, bucket, periodType, resolveWeekBarDate);
        return out;
    }

    private static List<StockBarAgg> aggregateByMonthKey(List<Total> dailiesAsc, String periodType) {
        if (dailiesAsc.isEmpty()) {
            return new ArrayList<>();
        }
        List<StockBarAgg> out = new ArrayList<>();
        List<Total> bucket = new ArrayList<>();
        LocalDate first = LocalDate.parse(dailiesAsc.get(0).getDate(), Constants.DB_DATE_FORMATTER);
        YearMonth ym = YearMonth.from(first);

        for (Total t : dailiesAsc) {
            LocalDate d = LocalDate.parse(t.getDate(), Constants.DB_DATE_FORMATTER);
            YearMonth cur = YearMonth.from(d);
            if (!cur.equals(ym)) {
                flushBucket(out, bucket, periodType, null);
                bucket.clear();
                ym = cur;
            }
            bucket.add(t);
        }
        flushBucket(out, bucket, periodType, null);
        return out;
    }

    private static List<StockBarAgg> aggregateByQuarterKey(List<Total> dailiesAsc, String periodType) {
        if (dailiesAsc.isEmpty()) {
            return new ArrayList<>();
        }
        List<StockBarAgg> out = new ArrayList<>();
        List<Total> bucket = new ArrayList<>();
        LocalDate first = LocalDate.parse(dailiesAsc.get(0).getDate(), Constants.DB_DATE_FORMATTER);
        long bucketKey = calendarQuarterKey(first);

        for (Total t : dailiesAsc) {
            LocalDate d = LocalDate.parse(t.getDate(), Constants.DB_DATE_FORMATTER);
            long k = calendarQuarterKey(d);
            if (k != bucketKey) {
                flushBucket(out, bucket, periodType, null);
                bucket.clear();
                bucketKey = k;
            }
            bucket.add(t);
        }
        flushBucket(out, bucket, periodType, null);
        return out;
    }

    private static long calendarQuarterKey(LocalDate d) {
        int q = (d.getMonthValue() - 1) / 3 + 1;
        return (long) d.getYear() * 10L + q;
    }

    private static void flushBucket(
            List<StockBarAgg> out,
            List<Total> bucket,
            String periodType,
            Function<LocalDate, String> resolveWeekBarDate) {
        if (bucket.isEmpty()) {
            return;
        }
        out.add(bucketToBar(bucket, periodType, resolveWeekBarDate));
    }

    private static StockBarAgg bucketToBar(
            List<Total> bucket, String periodType, Function<LocalDate, String> resolveWeekBarDate) {
        Total first = bucket.get(0);
        Total last = bucket.get(bucket.size() - 1);
        LocalDate lastD = LocalDate.parse(last.getDate(), Constants.DB_DATE_FORMATTER);
        String barDate = stableBarDateForPeriod(periodType, lastD, resolveWeekBarDate);
        BigDecimal high = maxHigh(bucket);
        BigDecimal low = minLow(bucket);
        long volSum = 0L;
        for (Total t : bucket) {
            if (t.getVolume() != null) {
                volSum += t.getVolume().longValue();
            }
        }
        return StockBarAgg.builder()
                .code(last.getCode())
                .periodType(periodType)
                .firstTradeDate(first.getDate())
                .barDate(barDate)
                .lastTradeDate(last.getDate())
                .open(BigDecimal.valueOf(first.getOpen()))
                .high(high)
                .low(low)
                .close(BigDecimal.valueOf(last.getClose()))
                .volume(volSum)
                .build();
    }

    /**
     * 与 {@link #aggregateByWeekKey} 等分组一致；锚点取周期首日，保证同一周期内 bar_date 不变。
     */
    private static String stableBarDateForPeriod(
            String periodType,
            LocalDate anyDayInBucketEnd,
            Function<LocalDate, String> resolveWeekBarDate) {
        if (PT_WEEK.equals(periodType)) {
            if (resolveWeekBarDate != null) {
                String resolved = resolveWeekBarDate.apply(anyDayInBucketEnd);
                if (resolved != null && !resolved.isEmpty()) {
                    return resolved;
                }
            }
            LocalDate weekStart = anyDayInBucketEnd.with(WEEK_FIELDS.dayOfWeek(), 1L);
            return weekStart.format(Constants.DB_DATE_FORMATTER);
        }
        if (PT_MONTH.equals(periodType)) {
            return YearMonth.from(anyDayInBucketEnd).atDay(1).format(Constants.DB_DATE_FORMATTER);
        }
        if (PT_QUARTER.equals(periodType)) {
            return firstDayOfCalendarQuarter(anyDayInBucketEnd).format(Constants.DB_DATE_FORMATTER);
        }
        throw new IllegalArgumentException("unknown periodType: " + periodType);
    }

    private static LocalDate firstDayOfCalendarQuarter(LocalDate d) {
        int q0 = (d.getMonthValue() - 1) / 3;
        int startMonth = q0 * 3 + 1;
        return LocalDate.of(d.getYear(), startMonth, 1);
    }

    private static BigDecimal maxHigh(List<Total> bucket) {
        BigDecimal best = null;
        for (Total t : bucket) {
            BigDecimal bd = BigDecimal.valueOf(t.getHigh());
            if (best == null || bd.compareTo(best) > 0) {
                best = bd;
            }
        }
        return best != null ? best : BigDecimal.ZERO;
    }

    private static BigDecimal minLow(List<Total> bucket) {
        BigDecimal best = null;
        for (Total t : bucket) {
            BigDecimal bd = BigDecimal.valueOf(t.getLow());
            if (best == null || bd.compareTo(best) < 0) {
                best = bd;
            }
        }
        return best != null ? best : BigDecimal.ZERO;
    }

}
