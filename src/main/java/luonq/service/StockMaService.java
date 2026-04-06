package luonq.service;

import bean.MA;
import bean.StockMa;
import bean.Total;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import luonq.indicator.MovingAverageSma;
import luonq.mapper.MaMapper;
import luonq.mapper.StockDataMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import util.Constants;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.WeekFields;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * 从 {@code option/weekOption} 读取股票列表，按年表拉取 OHLC，计算 day/week/month/quarter 均线并写入 {@code ma} 表。
 */
@Service
@Slf4j
public class StockMaService {

    public static final String TYPE_DAY = "day";
    public static final String TYPE_WEEK = "week";
    public static final String TYPE_MONTH = "month";
    /** 季线：ISO 季度，每季最后一个交易日为锚点 date */
    public static final String TYPE_QUARTER = "quarter";

    private static final int MIN_YEAR = 2022;
    private static final LocalDate MIN_DATE = LocalDate.of(MIN_YEAR, 1, 1);
    private static final int UPSERT_BATCH = 500;
    private static final WeekFields US_WEEK = WeekFields.of(Locale.US);

    /** 日线增量：在已有 MA 水位线前多取若干日历日，保证 ma60 有足够交易日窗口 */
    private static final int INCR_DAY_LOOKBACK_DAYS = 200;
    /** 周线增量：有历史时只取近若干天日线重算周线（需覆盖约 60 个交易周以稳定 ma60，约 800 日历日） */
    private static final int INCR_WEEK_TAIL_DAYS = 800;
    /** 月线增量：约 6.3 年覆盖 ma60；无历史则从 {@value #MIN_YEAR} 年起 */
    private static final int INCR_MONTH_TAIL_DAYS = 2300;
    /** 季线增量：约 60 季需 15 年；无历史则从 {@value #MIN_YEAR} 年起 */
    private static final int INCR_QUARTER_TAIL_DAYS = 5600;

    @Autowired
    private StockDataMapper stockDataMapper;

    @Autowired
    private MaMapper maMapper;

    @Autowired
    private ChartDataChangeNotifier chartDataChangeNotifier;

    @Autowired
    private TrackedStockCodesProvider trackedStockCodesProvider;

    /**
     * weekOption 每行一个 code，空行与首尾空白忽略。
     */
    public List<String> loadTrackedCodes() {
        return trackedStockCodesProvider.loadAll();
    }

    /**
     * 全量：对 weekOption 中全部股票，从 {@value #MIN_YEAR} 年到 {@code endDate} 重建各周期 type 并 upsert。
     */
    public int syncFull(LocalDate endDate, List<String> codesOverride) {
        List<String> codes = codesOverride != null ? codesOverride : loadTrackedCodes();
        if (CollectionUtils.isEmpty(codes)) {
            log.warn("no codes to sync");
            return 0;
        }
        int totalRows = 0;
        for (String code : codes) {
            totalRows += recomputeFullAllTypes(code, endDate);
        }
        log.info("syncFull endDate={} codes={} totalMaRows={}", endDate, codes.size(), totalRows);
        if (totalRows > 0) {
            chartDataChangeNotifier.notifyAllSymbolsChanged();
        }
        return totalRows;
    }

    /**
     * 增量：按 {@code ma} 表水位线只算必要区间。日线仅重算新交易日；周/月/季只在有限日历窗口内重算（行数很少）。
     */
    public int syncIncremental(LocalDate endDate) {
        List<String> codes = loadTrackedCodes();
        if (CollectionUtils.isEmpty(codes)) {
            log.warn("no codes to sync");
            return 0;
        }
        int totalRows = 0;
        for (String code : codes) {
            totalRows += recomputeIncrementalAllTypes(code, endDate);
        }
        log.info("syncIncremental endDate={} codes={} totalMaRows={}", endDate, codes.size(), totalRows);
        if (totalRows > 0) {
            chartDataChangeNotifier.notifyAllSymbolsChanged();
        }
        return totalRows;
    }

    /**
     * 仅计算指定股票（调试或补数），走<strong>全量</strong>重算。
     */
    public int syncForCode(String code, LocalDate endDate) {
        if (StringUtils.isBlank(code)) {
            return 0;
        }
        String c = code.trim();
        int n = recomputeFullAllTypes(c, endDate);
        if (n > 0) {
            chartDataChangeNotifier.notifySymbolChanged(c);
        }
        return n;
    }

    /**
     * 单票<strong>增量</strong>（调试）。
     */
    public int syncForCodeIncremental(String code, LocalDate endDate) {
        if (StringUtils.isBlank(code)) {
            return 0;
        }
        String c = code.trim();
        int n = recomputeIncrementalAllTypes(c, endDate);
        if (n > 0) {
            chartDataChangeNotifier.notifySymbolChanged(c);
        }
        return n;
    }

    private int recomputeFullAllTypes(String code, LocalDate endDate) {
        List<Total> dailies = loadDailyTotalsRange(code, MIN_DATE, endDate);
        if (dailies.isEmpty()) {
            log.debug("no OHLC for code={} through {}", code, endDate);
            return 0;
        }

        List<StockMa> all = new ArrayList<>();
        all.addAll(buildTypeRows(code, TYPE_DAY, dailies));

        List<Total> weekBars = aggregateByWeek(dailies);
        all.addAll(buildTypeRows(code, TYPE_WEEK, weekBars));

        List<Total> monthBars = aggregateByMonth(dailies);
        all.addAll(buildTypeRows(code, TYPE_MONTH, monthBars));

        List<Total> quarterBars = aggregateByQuarter(dailies);
        all.addAll(buildTypeRows(code, TYPE_QUARTER, quarterBars));

        upsertPartitioned(all);
        log.info("ma full upsert code={} dayBars={} weekBars={} monthBars={} quarterBars={} rows={}",
                code, dailies.size(), weekBars.size(), monthBars.size(), quarterBars.size(), all.size());
        return all.size();
    }

    private int recomputeIncrementalAllTypes(String code, LocalDate endDate) {
        String endStr = endDate.format(Constants.DB_DATE_FORMATTER);
        int rows = 0;
        rows += syncDayMaIncremental(code, endDate, endStr);
        rows += syncWeekMaIncremental(code, endDate, endStr);
        rows += syncMonthMaIncremental(code, endDate, endStr);
        rows += syncQuarterMaIncremental(code, endDate, endStr);
        return rows;
    }

    private int syncDayMaIncremental(String code, LocalDate endDate, String endStr) {
        String lastDay = maMapper.selectMaxDate(code, TYPE_DAY);
        if (lastDay != null && lastDay.compareTo(endStr) >= 0) {
            return 0;
        }
        LocalDate loadStart = MIN_DATE;
        if (lastDay != null) {
            LocalDate ld = LocalDate.parse(lastDay, Constants.DB_DATE_FORMATTER);
            loadStart = ld.minusDays(INCR_DAY_LOOKBACK_DAYS);
            if (loadStart.isBefore(MIN_DATE)) {
                loadStart = MIN_DATE;
            }
        }
        List<Total> dailies = loadDailyTotalsRange(code, loadStart, endDate);
        if (dailies.isEmpty()) {
            return 0;
        }
        List<StockMa> built = buildTypeRows(code, TYPE_DAY, dailies);
        List<StockMa> toWrite;
        if (lastDay == null) {
            toWrite = built.stream()
                    .filter(r -> r.getDate().compareTo(endStr) <= 0)
                    .collect(Collectors.toList());
        } else {
            toWrite = built.stream()
                    .filter(r -> r.getDate().compareTo(lastDay) > 0 && r.getDate().compareTo(endStr) <= 0)
                    .collect(Collectors.toList());
        }
        upsertPartitioned(toWrite);
        if (!toWrite.isEmpty()) {
            log.debug("ma incremental day code={} upsertRows={}", code, toWrite.size());
        }
        return toWrite.size();
    }

    private int syncWeekMaIncremental(String code, LocalDate endDate, String endStr) {
        String lastW = maMapper.selectMaxDate(code, TYPE_WEEK);
        LocalDate loadStart = lastW == null
                ? MIN_DATE
                : maxDate(MIN_DATE, endDate.minusDays(INCR_WEEK_TAIL_DAYS));
        List<Total> dailies = loadDailyTotalsRange(code, loadStart, endDate);
        if (dailies.isEmpty()) {
            return 0;
        }
        List<Total> weekBars = aggregateByWeek(dailies);
        if (weekBars.isEmpty()) {
            return 0;
        }
        List<StockMa> built = buildTypeRows(code, TYPE_WEEK, weekBars);
        List<StockMa> toWrite = built.stream()
                .filter(r -> r.getDate().compareTo(endStr) <= 0)
                .collect(Collectors.toList());
        upsertPartitioned(toWrite);
        return toWrite.size();
    }

    private int syncMonthMaIncremental(String code, LocalDate endDate, String endStr) {
        String lastM = maMapper.selectMaxDate(code, TYPE_MONTH);
        LocalDate loadStart = lastM == null
                ? MIN_DATE
                : maxDate(MIN_DATE, endDate.minusDays(INCR_MONTH_TAIL_DAYS));
        List<Total> dailies = loadDailyTotalsRange(code, loadStart, endDate);
        if (dailies.isEmpty()) {
            return 0;
        }
        List<Total> monthBars = aggregateByMonth(dailies);
        if (monthBars.isEmpty()) {
            return 0;
        }
        List<StockMa> built = buildTypeRows(code, TYPE_MONTH, monthBars);
        List<StockMa> toWrite = built.stream()
                .filter(r -> r.getDate().compareTo(endStr) <= 0)
                .collect(Collectors.toList());
        upsertPartitioned(toWrite);
        return toWrite.size();
    }

    private int syncQuarterMaIncremental(String code, LocalDate endDate, String endStr) {
        String lastQ = maMapper.selectMaxDate(code, TYPE_QUARTER);
        LocalDate loadStart = lastQ == null
                ? MIN_DATE
                : maxDate(MIN_DATE, endDate.minusDays(INCR_QUARTER_TAIL_DAYS));
        List<Total> dailies = loadDailyTotalsRange(code, loadStart, endDate);
        if (dailies.isEmpty()) {
            return 0;
        }
        List<Total> quarterBars = aggregateByQuarter(dailies);
        if (quarterBars.isEmpty()) {
            return 0;
        }
        List<StockMa> built = buildTypeRows(code, TYPE_QUARTER, quarterBars);
        List<StockMa> toWrite = built.stream()
                .filter(r -> r.getDate().compareTo(endStr) <= 0)
                .collect(Collectors.toList());
        upsertPartitioned(toWrite);
        return toWrite.size();
    }

    private static LocalDate maxDate(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private void upsertPartitioned(List<StockMa> rows) {
        if (CollectionUtils.isEmpty(rows)) {
            return;
        }
        for (List<StockMa> part : Lists.partition(rows, UPSERT_BATCH)) {
            maMapper.batchUpsertMa(part);
        }
    }

    private List<StockMa> buildTypeRows(String code, String type, List<Total> barsChrono) {
        List<Double> closes = barsChrono.stream().map(Total::getClose).collect(Collectors.toList());
        List<String> dates = barsChrono.stream().map(Total::getDate).collect(Collectors.toList());
        List<MA> mas = MovingAverageSma.computeSeries(closes, dates, MovingAverageSma.SCALE_DB);
        List<StockMa> rows = new ArrayList<>(mas.size());
        for (MA m : mas) {
            rows.add(StockMa.builder()
                    .code(code)
                    .type(type)
                    .date(m.getDate())
                    .ma5(m.getMa5())
                    .ma10(m.getMa10())
                    .ma20(m.getMa20())
                    .ma30(m.getMa30())
                    .ma60(m.getMa60())
                    .build());
        }
        return rows;
    }

    private List<Total> loadDailyTotalsRange(String code, LocalDate startDate, LocalDate endDate) {
        LocalDate effStart = startDate.isBefore(MIN_DATE) ? MIN_DATE : startDate;
        List<String> years = stockDataMapper.listStockDataYears();
        if (years == null || years.isEmpty()) {
            return Collections.emptyList();
        }
        int yStart = effStart.getYear();
        int yEnd = endDate.getYear();
        List<Total> merged = new ArrayList<>();
        for (String y : years) {
            int yi = Integer.parseInt(y);
            if (yi < yStart || yi > yEnd) {
                continue;
            }
            List<Total> chunk = stockDataMapper.queryByCode(y, code, "asc");
            if (chunk == null || chunk.isEmpty()) {
                continue;
            }
            for (Total t : chunk) {
                if (StringUtils.isBlank(t.getDate())) {
                    continue;
                }
                LocalDate d = LocalDate.parse(t.getDate(), Constants.DB_DATE_FORMATTER);
                if (d.isBefore(effStart) || d.isAfter(endDate)) {
                    continue;
                }
                merged.add(t);
            }
        }
        merged.sort(Comparator.comparing(Total::getDate));
        return merged;
    }

    /**
     * 自然周（{@link Locale#US}）：每周取该周内最后一个交易日的 bar（收盘价作为周线收盘）。
     */
    private List<Total> aggregateByWeek(List<Total> dailiesAsc) {
        if (dailiesAsc.isEmpty()) {
            return Collections.emptyList();
        }
        List<Total> out = new ArrayList<>();
        List<Total> bucket = new ArrayList<>();
        LocalDate first = LocalDate.parse(dailiesAsc.get(0).getDate(), Constants.DB_DATE_FORMATTER);
        int weekKey = first.get(US_WEEK.weekOfWeekBasedYear());
        int weekYear = first.get(US_WEEK.weekBasedYear());

        for (Total t : dailiesAsc) {
            LocalDate d = LocalDate.parse(t.getDate(), Constants.DB_DATE_FORMATTER);
            int wk = d.get(US_WEEK.weekOfWeekBasedYear());
            int wy = d.get(US_WEEK.weekBasedYear());
            if (wk != weekKey || wy != weekYear) {
                flushMergedPeriodBar(out, bucket);
                bucket.clear();
                weekKey = wk;
                weekYear = wy;
            }
            bucket.add(t);
        }
        flushMergedPeriodBar(out, bucket);
        return out;
    }

    /**
     * 周期 K：{@code date} 取<strong>周期内首个交易日</strong>（与图表/十字光标对齐周期起点）；OHLC 为周期内合并（开=首日开盘，收=末日收盘，高/低=区间内极值）。
     * 均线/BOLL 等仍基于合并后的收盘价序列。
     */
    private static void flushMergedPeriodBar(List<Total> out, List<Total> bucket) {
        if (bucket.isEmpty()) {
            return;
        }
        Total first = bucket.get(0);
        Total last = bucket.get(bucket.size() - 1);
        double high = bucket.stream().mapToDouble(Total::getHigh).max().orElse(last.getHigh());
        double low = bucket.stream().mapToDouble(Total::getLow).min().orElse(last.getLow());
        BigDecimal volSum = BigDecimal.ZERO;
        for (Total t : bucket) {
            if (t.getVolume() != null) {
                volSum = volSum.add(t.getVolume());
            }
        }
        Total merged = new Total();
        merged.setCode(last.getCode());
        merged.setDate(first.getDate());
        merged.setOpen(first.getOpen());
        merged.setHigh(high);
        merged.setLow(low);
        merged.setClose(last.getClose());
        merged.setVolume(volSum.compareTo(BigDecimal.ZERO) == 0 ? null : volSum);
        out.add(merged);
    }

    /**
     * 每个自然月合并为一条月线；合并行的 {@code date} 为该月首个交易日（见 {@link #flushMergedPeriodBar}）。
     */
    private List<Total> aggregateByMonth(List<Total> dailiesAsc) {
        if (dailiesAsc.isEmpty()) {
            return Collections.emptyList();
        }
        List<Total> out = new ArrayList<>();
        List<Total> bucket = new ArrayList<>();
        LocalDate first = LocalDate.parse(dailiesAsc.get(0).getDate(), Constants.DB_DATE_FORMATTER);
        YearMonth ym = YearMonth.from(first);

        for (Total t : dailiesAsc) {
            LocalDate d = LocalDate.parse(t.getDate(), Constants.DB_DATE_FORMATTER);
            YearMonth cur = YearMonth.from(d);
            if (!cur.equals(ym)) {
                flushMergedPeriodBar(out, bucket);
                bucket.clear();
                ym = cur;
            }
            bucket.add(t);
        }
        flushMergedPeriodBar(out, bucket);
        return out;
    }

    /**
     * 公历季（Q1–Q4）合并为季 K；合并行的 {@code date} 为该季首个交易日（见 {@link #flushMergedPeriodBar}）。
     * 使用自然年+季分组，与 Java 8 兼容（{@code IsoFields.YEAR_OF_QUARTER} 仅 Java 9+）。
     */
    private List<Total> aggregateByQuarter(List<Total> dailiesAsc) {
        if (dailiesAsc.isEmpty()) {
            return Collections.emptyList();
        }
        List<Total> out = new ArrayList<>();
        List<Total> bucket = new ArrayList<>();
        LocalDate first = LocalDate.parse(dailiesAsc.get(0).getDate(), Constants.DB_DATE_FORMATTER);
        long bucketKey = calendarQuarterKey(first);

        for (Total t : dailiesAsc) {
            LocalDate d = LocalDate.parse(t.getDate(), Constants.DB_DATE_FORMATTER);
            long k = calendarQuarterKey(d);
            if (k != bucketKey) {
                flushMergedPeriodBar(out, bucket);
                bucket.clear();
                bucketKey = k;
            }
            bucket.add(t);
        }
        flushMergedPeriodBar(out, bucket);
        return out;
    }

    private static long calendarQuarterKey(LocalDate d) {
        int q = (d.getMonthValue() - 1) / 3 + 1;
        return (long) d.getYear() * 10L + q;
    }

    /** 与 {@link StockBollService} 共用：年表日线（日期升序）。 */
    public List<Total> fetchDailiesAsc(String code, LocalDate startDate, LocalDate endDate) {
        return loadDailyTotalsRange(code, startDate, endDate);
    }

    public List<Total> aggregateWeekBars(List<Total> dailiesAsc) {
        return aggregateByWeek(dailiesAsc);
    }

    public List<Total> aggregateMonthBars(List<Total> dailiesAsc) {
        return aggregateByMonth(dailiesAsc);
    }

    public List<Total> aggregateQuarterBars(List<Total> dailiesAsc) {
        return aggregateByQuarter(dailiesAsc);
    }

    /**
     * K 线图表：按 {@code ma} 表读取均线（与 {@link #TYPE_DAY} / week / month / quarter 一致），闭区间、按日期升序。
     */
    public List<StockMa> queryMaRange(String code, String maType, String fromInclusive, String toInclusive) {
        if (StringUtils.isBlank(code) || StringUtils.isBlank(maType)) {
            return Collections.emptyList();
        }
        if (StringUtils.isBlank(fromInclusive) || StringUtils.isBlank(toInclusive)) {
            return Collections.emptyList();
        }
        return maMapper.selectByCodeTypeBetween(code.trim(), maType, fromInclusive, toInclusive);
    }
}
