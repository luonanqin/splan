package luonq.aggregate;

import bean.PeriodOhlcvBar;
import bean.Total;
import util.Constants;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * 从<strong>按日期升序</strong>的日线序列聚合周/月/季 K，锚点与 {@code stock_bar_agg} 一致：
 * {@code barDate} 为周期内最后交易日，{@code firstTradeDate} 为周期内首个交易日。
 */
public final class PeriodOhlcvAggregator {

    private static final WeekFields US_WEEK = WeekFields.of(Locale.US);

    private PeriodOhlcvAggregator() {}

    public static List<PeriodOhlcvBar> aggregateWeek(List<Total> dailiesAsc) {
        if (dailiesAsc.isEmpty()) {
            return new ArrayList<>();
        }
        List<PeriodOhlcvBar> out = new ArrayList<>();
        List<Total> bucket = new ArrayList<>();
        LocalDate first = LocalDate.parse(dailiesAsc.get(0).getDate(), Constants.DB_DATE_FORMATTER);
        int weekKey = first.get(US_WEEK.weekOfWeekBasedYear());
        int weekYear = first.get(US_WEEK.weekBasedYear());

        for (Total t : dailiesAsc) {
            LocalDate d = LocalDate.parse(t.getDate(), Constants.DB_DATE_FORMATTER);
            int wk = d.get(US_WEEK.weekOfWeekBasedYear());
            int wy = d.get(US_WEEK.weekBasedYear());
            if (wk != weekKey || wy != weekYear) {
                flushBucket(out, bucket);
                bucket.clear();
                weekKey = wk;
                weekYear = wy;
            }
            bucket.add(t);
        }
        flushBucket(out, bucket);
        return out;
    }

    public static List<PeriodOhlcvBar> aggregateMonth(List<Total> dailiesAsc) {
        if (dailiesAsc.isEmpty()) {
            return new ArrayList<>();
        }
        List<PeriodOhlcvBar> out = new ArrayList<>();
        List<Total> bucket = new ArrayList<>();
        LocalDate first = LocalDate.parse(dailiesAsc.get(0).getDate(), Constants.DB_DATE_FORMATTER);
        YearMonth ym = YearMonth.from(first);

        for (Total t : dailiesAsc) {
            LocalDate d = LocalDate.parse(t.getDate(), Constants.DB_DATE_FORMATTER);
            YearMonth cur = YearMonth.from(d);
            if (!cur.equals(ym)) {
                flushBucket(out, bucket);
                bucket.clear();
                ym = cur;
            }
            bucket.add(t);
        }
        flushBucket(out, bucket);
        return out;
    }

    public static List<PeriodOhlcvBar> aggregateQuarter(List<Total> dailiesAsc) {
        if (dailiesAsc.isEmpty()) {
            return new ArrayList<>();
        }
        List<PeriodOhlcvBar> out = new ArrayList<>();
        List<Total> bucket = new ArrayList<>();
        LocalDate first = LocalDate.parse(dailiesAsc.get(0).getDate(), Constants.DB_DATE_FORMATTER);
        long bucketKey = calendarQuarterKey(first);

        for (Total t : dailiesAsc) {
            LocalDate d = LocalDate.parse(t.getDate(), Constants.DB_DATE_FORMATTER);
            long k = calendarQuarterKey(d);
            if (k != bucketKey) {
                flushBucket(out, bucket);
                bucket.clear();
                bucketKey = k;
            }
            bucket.add(t);
        }
        flushBucket(out, bucket);
        return out;
    }

    private static long calendarQuarterKey(LocalDate d) {
        int q = (d.getMonthValue() - 1) / 3 + 1;
        return (long) d.getYear() * 10L + q;
    }

    private static void flushBucket(List<PeriodOhlcvBar> out, List<Total> bucket) {
        if (bucket.isEmpty()) {
            return;
        }
        out.add(bucketToBar(bucket));
    }

    private static PeriodOhlcvBar bucketToBar(List<Total> bucket) {
        Total first = bucket.get(0);
        Total last = bucket.get(bucket.size() - 1);
        double high = bucket.stream().mapToDouble(Total::getHigh).max().orElse(last.getHigh());
        double low = bucket.stream().mapToDouble(Total::getLow).min().orElse(last.getLow());
        long volSum = 0L;
        for (Total t : bucket) {
            if (t.getVolume() != null) {
                volSum += t.getVolume().longValue();
            }
        }
        return PeriodOhlcvBar.builder()
                .code(last.getCode())
                .firstTradeDate(first.getDate())
                .barDate(last.getDate())
                .open(first.getOpen())
                .high(high)
                .low(low)
                .close(last.getClose())
                .volume(volSum)
                .build();
    }
}
