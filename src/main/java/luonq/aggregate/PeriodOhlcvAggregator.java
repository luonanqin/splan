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
import java.util.Locale;

/**
 * 从<strong>按日期升序</strong>的日线序列聚合周/月/季 K，直接产出 {@link StockBarAgg}（与 {@code stock_bar_agg} 一致）：
 * {@code barDate} 为周期内最后交易日，{@code firstTradeDate} 为周期内首个交易日；OHLC 用 {@link BigDecimal} 聚合。
 */
public final class PeriodOhlcvAggregator {

    private static final WeekFields US_WEEK = WeekFields.of(Locale.US);

    /** 与 {@link luonq.service.StockBarAggService} 及表 {@code period_type} 一致 */
    private static final String PT_WEEK = "week";
    private static final String PT_MONTH = "month";
    private static final String PT_QUARTER = "quarter";

    private PeriodOhlcvAggregator() {}

    public static List<StockBarAgg> aggregateWeek(List<Total> dailiesAsc) {
        return aggregateByWeekKey(dailiesAsc, PT_WEEK);
    }

    public static List<StockBarAgg> aggregateMonth(List<Total> dailiesAsc) {
        return aggregateByMonthKey(dailiesAsc, PT_MONTH);
    }

    public static List<StockBarAgg> aggregateQuarter(List<Total> dailiesAsc) {
        return aggregateByQuarterKey(dailiesAsc, PT_QUARTER);
    }

    private static List<StockBarAgg> aggregateByWeekKey(List<Total> dailiesAsc, String periodType) {
        if (dailiesAsc.isEmpty()) {
            return new ArrayList<>();
        }
        List<StockBarAgg> out = new ArrayList<>();
        List<Total> bucket = new ArrayList<>();
        LocalDate first = LocalDate.parse(dailiesAsc.get(0).getDate(), Constants.DB_DATE_FORMATTER);
        int weekKey = first.get(US_WEEK.weekOfWeekBasedYear());
        int weekYear = first.get(US_WEEK.weekBasedYear());

        for (Total t : dailiesAsc) {
            LocalDate d = LocalDate.parse(t.getDate(), Constants.DB_DATE_FORMATTER);
            int wk = d.get(US_WEEK.weekOfWeekBasedYear());
            int wy = d.get(US_WEEK.weekBasedYear());
            if (wk != weekKey || wy != weekYear) {
                flushBucket(out, bucket, periodType);
                bucket.clear();
                weekKey = wk;
                weekYear = wy;
            }
            bucket.add(t);
        }
        flushBucket(out, bucket, periodType);
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
                flushBucket(out, bucket, periodType);
                bucket.clear();
                ym = cur;
            }
            bucket.add(t);
        }
        flushBucket(out, bucket, periodType);
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
                flushBucket(out, bucket, periodType);
                bucket.clear();
                bucketKey = k;
            }
            bucket.add(t);
        }
        flushBucket(out, bucket, periodType);
        return out;
    }

    private static long calendarQuarterKey(LocalDate d) {
        int q = (d.getMonthValue() - 1) / 3 + 1;
        return (long) d.getYear() * 10L + q;
    }

    private static void flushBucket(List<StockBarAgg> out, List<Total> bucket, String periodType) {
        if (bucket.isEmpty()) {
            return;
        }
        out.add(bucketToBar(bucket, periodType));
    }

    private static StockBarAgg bucketToBar(List<Total> bucket, String periodType) {
        Total first = bucket.get(0);
        Total last = bucket.get(bucket.size() - 1);
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
                .barDate(last.getDate())
                .open(BigDecimal.valueOf(first.getOpen()))
                .high(high)
                .low(low)
                .close(BigDecimal.valueOf(last.getClose()))
                .volume(volSum)
                .build();
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
