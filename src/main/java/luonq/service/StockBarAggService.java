package luonq.service;

import bean.StockBarAgg;
import bean.Total;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import luonq.aggregate.PeriodOhlcvAggregator;
import luonq.mapper.StockBarAggMapper;
import luonq.mapper.TradeCalendarMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import util.Constants;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 周/月/季 K：由日线聚合写入 {@code stock_bar_agg}，并提供图表查询（{@link #queryByCodePeriodBetween} 等）。
 * 跟踪标的见 {@link TrackedStockCodesProvider}；分组规则与均线/BOLL 同步任务一致，锚点 {@code bar_date} 见 {@link PeriodOhlcvAggregator}
 * （周线为 {@code trade_calendar} 在该 ISO 周内首个交易日，无日历则 ISO 周一）。
 */
@Service
@Slf4j
public class StockBarAggService {

    public static final String PERIOD_WEEK = "week";
    public static final String PERIOD_MONTH = "month";
    public static final String PERIOD_QUARTER = "quarter";

    private static final int MIN_YEAR = 2022;
    private static final LocalDate MIN_DATE = LocalDate.of(MIN_YEAR, 1, 1);
    private static final int UPSERT_BATCH = 500;

    private static final int INCR_WEEK_TAIL_DAYS = 800;
    private static final int INCR_MONTH_TAIL_DAYS = 2300;
    private static final int INCR_QUARTER_TAIL_DAYS = 5600;

    @Autowired
    private StockDailyDataService stockDailyDataService;

    @Autowired
    private TrackedStockCodesProvider trackedStockCodesProvider;

    @Autowired
    private StockBarAggMapper stockBarAggMapper;

    @Autowired
    private TradeCalendarMapper tradeCalendarMapper;

    @Autowired
    private ChartDataChangeNotifier chartDataChangeNotifier;

    /**
     * 全量：weekOption 全部标的，自 {@value #MIN_YEAR} 年起至 {@code endDate}。
     */
    public int syncFull(LocalDate endDate, List<String> codesOverride, boolean notifyChart) {
        List<String> codes = codesOverride != null ? codesOverride : trackedStockCodesProvider.loadAll();
        if (CollectionUtils.isEmpty(codes)) {
            log.warn("stock_bar_agg syncFull: no codes");
            return 0;
        }
        int total = 0;
        for (String code : codes) {
            total += recomputeFullForCode(code, endDate);
        }
        log.info("stock_bar_agg syncFull endDate={} codes={} totalRows={}", endDate, codes.size(), total);
        if (total > 0 && notifyChart) {
            chartDataChangeNotifier.notifyAllSymbolsChanged();
        }
        return total;
    }

    /**
     * 增量：与 {@link StockMaService#syncIncremental} 相同的日线回溯窗口（见 {@link StockDailyDataService}），只重算可能受影响的周/月/季 K。
     * <p>
     * 非日 K 的 OHLC 始终来自<strong>窗口内全部日线</strong>聚合（或 merge 路径下 {@code last_trade_date} 之后<strong>所有</strong>新日线），
     * 不会只用「asOf 当日」一条日线；例如一次补 7–10 号则 7、8、9、10 都会参与当周/月/季高低收量。
     */
    public int syncIncremental(LocalDate endDate, boolean notifyChart) {
        List<String> codes = trackedStockCodesProvider.loadAll();
        if (CollectionUtils.isEmpty(codes)) {
            log.warn("stock_bar_agg syncIncremental: no codes");
            return 0;
        }
        int total = 0;
        for (String code : codes) {
            total += recomputeIncrementalForCode(code, endDate);
        }
        log.info("stock_bar_agg syncIncremental endDate={} codes={} totalRows={}", endDate, codes.size(), total);
        if (total > 0 && notifyChart) {
            chartDataChangeNotifier.notifyAllSymbolsChanged();
        }
        return total;
    }

    /** 单票全量（补数 / 调试）。 */
    public int syncForCode(String code, LocalDate endDate, boolean notifyChart) {
        if (StringUtils.isBlank(code)) {
            return 0;
        }
        String c = code.trim();
        int n = recomputeFullForCode(c, endDate);
        if (n > 0 && notifyChart) {
            chartDataChangeNotifier.notifySymbolChanged(c);
        }
        return n;
    }

    /** 单票增量。 */
    public int syncForCodeIncremental(String code, LocalDate endDate, boolean notifyChart) {
        if (StringUtils.isBlank(code)) {
            return 0;
        }
        String c = code.trim();
        int n = recomputeIncrementalForCode(c, endDate);
        if (n > 0 && notifyChart) {
            chartDataChangeNotifier.notifySymbolChanged(c);
        }
        return n;
    }

    /**
     * 日 K 写入后由 Massive 等链路调用：不重发图表通知（上游已通知）。
     */
    public int syncIncrementalChain(LocalDate endDate) {
        return syncIncremental(endDate, false);
    }

    /**
     * 图表查询：按 {@code bar_date} 升序；{@code from}、{@code to} 为 yyyy-MM-dd 闭区间，可空表示无下界/上界。
     */
    public List<StockBarAgg> queryByCodePeriodBetween(String code, String periodType, String from, String to) {
        if (StringUtils.isBlank(code) || StringUtils.isBlank(periodType)) {
            return Collections.emptyList();
        }
        List<StockBarAgg> list = stockBarAggMapper.selectByCodePeriodBetween(
                code.trim(), periodType.trim(), from, to);
        return list != null ? list : Collections.emptyList();
    }

    /**
     * 最新 {@code limit} 根（按 {@code bar_date} 升序）；{@code toInclusive} 非空时仅含 bar_date ≤ toInclusive。
     */
    public List<StockBarAgg> queryLatestByCodePeriod(String code, String periodType, int limit, String toInclusive) {
        if (limit <= 0 || StringUtils.isBlank(code) || StringUtils.isBlank(periodType)) {
            return Collections.emptyList();
        }
        List<StockBarAgg> desc = stockBarAggMapper.selectLatestByCodePeriod(
                code.trim(), periodType.trim(), limit, toInclusive);
        if (desc == null || desc.isEmpty()) {
            return Collections.emptyList();
        }
        Collections.reverse(desc);
        return desc;
    }

    /**
     * 严格早于 {@code beforeExclusive} 的最近 {@code limit} 根，按 {@code bar_date} 升序。
     */
    public List<StockBarAgg> queryBeforeExclusive(String code, String periodType, String beforeExclusive, int limit) {
        if (limit <= 0 || StringUtils.isBlank(code) || StringUtils.isBlank(periodType) || StringUtils.isBlank(beforeExclusive)) {
            return Collections.emptyList();
        }
        List<StockBarAgg> desc = stockBarAggMapper.selectBeforeExclusive(
                code.trim(), periodType.trim(), beforeExclusive.trim(), limit);
        if (desc == null || desc.isEmpty()) {
            return Collections.emptyList();
        }
        Collections.reverse(desc);
        return desc;
    }

    private int recomputeFullForCode(String code, LocalDate endDate) {
        String endStr = endDate.format(Constants.DB_DATE_FORMATTER);
        List<Total> dailies = stockDailyDataService.fetchDailiesAsc(code, MIN_DATE, endDate);
        if (dailies.isEmpty()) {
            return 0;
        }
        List<StockBarAgg> all = new ArrayList<>();
        all.addAll(filterBarsUpToEnd(PeriodOhlcvAggregator.aggregateWeek(dailies, this::weekBarDateFromTradeCalendar), endStr));
        all.addAll(filterBarsUpToEnd(PeriodOhlcvAggregator.aggregateMonth(dailies), endStr));
        all.addAll(filterBarsUpToEnd(PeriodOhlcvAggregator.aggregateQuarter(dailies), endStr));
        upsertPartitioned(all);
        log.info("stock_bar_agg full code={} dailies={} rows={}", code, dailies.size(), all.size());
        return all.size();
    }

    private int recomputeIncrementalForCode(String code, LocalDate endDate) {
        String endStr = endDate.format(Constants.DB_DATE_FORMATTER);
        int n = 0;
        n += syncPeriodIncremental(code, endDate, endStr, PERIOD_WEEK, INCR_WEEK_TAIL_DAYS);
        n += syncPeriodIncremental(code, endDate, endStr, PERIOD_MONTH, INCR_MONTH_TAIL_DAYS);
        n += syncPeriodIncremental(code, endDate, endStr, PERIOD_QUARTER, INCR_QUARTER_TAIL_DAYS);
        return n;
    }

    private int syncPeriodIncremental(
            String code,
            LocalDate endDate,
            String endStr,
            String periodType,
            int tailDays) {
        String lastBar = stockBarAggMapper.selectMaxBarDate(code, periodType);
        LocalDate loadStart = lastBar == null
                ? MIN_DATE
                : maxDate(MIN_DATE, endDate.minusDays(tailDays));
        List<Total> dailies = stockDailyDataService.fetchDailiesAsc(code, loadStart, endDate);
        if (dailies.isEmpty()) {
            return 0;
        }
        List<StockBarAgg> bars;
        if (PERIOD_WEEK.equals(periodType)) {
            bars = PeriodOhlcvAggregator.aggregateWeek(dailies, this::weekBarDateFromTradeCalendar);
        } else if (PERIOD_MONTH.equals(periodType)) {
            bars = PeriodOhlcvAggregator.aggregateMonth(dailies);
        } else {
            bars = PeriodOhlcvAggregator.aggregateQuarter(dailies);
        }
        if (bars.isEmpty()) {
            return 0;
        }
        List<StockBarAgg> rows = new ArrayList<>(filterBarsUpToEnd(bars, endStr));
        applyIncrementalMergeForOpenPeriod(code, periodType, endDate, endStr, rows);
        upsertPartitioned(rows);
        return rows.size();
    }

    /**
     * 当前周期（含 {@code endDate} 的那根）若在库中已有记录且 {@code last_trade_date} &lt; endStr，
     * 则只把「上一 last_trade_date 之后」的日线并入 OHLC，避免整段重算。
     */
    private void applyIncrementalMergeForOpenPeriod(
            String code,
            String periodType,
            LocalDate endDate,
            String endStr,
            List<StockBarAgg> rows) {
        String anchor = periodAnchorBarDate(periodType, endDate);
        for (int i = 0; i < rows.size(); i++) {
            StockBarAgg computed = rows.get(i);
            if (!anchor.equals(computed.getBarDate())) {
                continue;
            }
            StockBarAgg existing = stockBarAggMapper.selectByCodePeriodBarDate(code, periodType, anchor);
            if (existing == null || StringUtils.isBlank(existing.getLastTradeDate())) {
                break;
            }
            String lt = existing.getLastTradeDate();
            if (lt.compareTo(endStr) >= 0) {
                break;
            }
            List<Total> tail = stockDailyDataService.fetchDailiesStrictlyAfter(code, lt, endDate);
            if (tail.isEmpty()) {
                break;
            }
            rows.set(i, mergeAggWithNewDailies(existing, tail));
            break;
        }
    }

    private String periodAnchorBarDate(String periodType, LocalDate endDate) {
        if (PERIOD_WEEK.equals(periodType)) {
            return weekBarDateFromTradeCalendar(endDate);
        }
        if (PERIOD_MONTH.equals(periodType)) {
            return YearMonth.from(endDate).atDay(1).format(Constants.DB_DATE_FORMATTER);
        }
        if (PERIOD_QUARTER.equals(periodType)) {
            return firstDayOfCalendarQuarter(endDate).format(Constants.DB_DATE_FORMATTER);
        }
        throw new IllegalArgumentException("periodType: " + periodType);
    }

    private static LocalDate firstDayOfCalendarQuarter(LocalDate d) {
        int q0 = (d.getMonthValue() - 1) / 3;
        int startMonth = q0 * 3 + 1;
        return LocalDate.of(d.getYear(), startMonth, 1);
    }

    private static StockBarAgg mergeAggWithNewDailies(StockBarAgg base, List<Total> tailAsc) {
        Total last = tailAsc.get(tailAsc.size() - 1);
        BigDecimal high = base.getHigh() != null ? base.getHigh() : BigDecimal.ZERO;
        BigDecimal low = base.getLow() != null ? base.getLow() : BigDecimal.ZERO;
        for (Total t : tailAsc) {
            BigDecimal th = BigDecimal.valueOf(t.getHigh());
            BigDecimal tl = BigDecimal.valueOf(t.getLow());
            if (th.compareTo(high) > 0) {
                high = th;
            }
            if (tl.compareTo(low) < 0) {
                low = tl;
            }
        }
        long addVol = 0L;
        for (Total t : tailAsc) {
            if (t.getVolume() != null) {
                addVol += t.getVolume().longValue();
            }
        }
        return StockBarAgg.builder()
                .code(base.getCode())
                .periodType(base.getPeriodType())
                .barDate(base.getBarDate())
                .firstTradeDate(base.getFirstTradeDate())
                .lastTradeDate(last.getDate())
                .open(base.getOpen() != null ? base.getOpen() : BigDecimal.ZERO)
                .high(high)
                .low(low)
                .close(BigDecimal.valueOf(last.getClose()))
                .volume(base.getVolume() + addVol)
                .build();
    }

    private static LocalDate maxDate(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private static List<StockBarAgg> filterBarsUpToEnd(List<StockBarAgg> bars, String endStr) {
        return bars.stream()
                .filter(b -> b.getBarDate().compareTo(endStr) <= 0)
                .collect(Collectors.toList());
    }

    /**
     * 周线 {@code bar_date}：{@code trade_calendar} 在 [ISO 周一, ISO 周日] 闭区间内最早日期；无记录则 ISO 周一。
     */
    private String weekBarDateFromTradeCalendar(LocalDate anyDayInIsoWeek) {
        LocalDate monday = anyDayInIsoWeek.with(WeekFields.ISO.dayOfWeek(), 1L);
        LocalDate sunday = monday.plusDays(6);
        String from = monday.format(Constants.DB_DATE_FORMATTER);
        String to = sunday.format(Constants.DB_DATE_FORMATTER);
        String min = tradeCalendarMapper.selectMinTradingDateBetween(from, to);
        if (min == null || min.isEmpty()) {
            return from;
        }
        return min;
    }

    private void upsertPartitioned(List<StockBarAgg> rows) {
        if (CollectionUtils.isEmpty(rows)) {
            return;
        }
        for (List<StockBarAgg> part : Lists.partition(rows, UPSERT_BATCH)) {
            stockBarAggMapper.batchUpsertStockBarAgg(part);
        }
    }
}
