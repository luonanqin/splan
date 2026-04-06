package luonq.service;

import bean.PeriodOhlcvBar;
import bean.StockBarAgg;
import bean.Total;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import luonq.aggregate.PeriodOhlcvAggregator;
import luonq.mapper.StockBarAggMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import util.Constants;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 由日线聚合周/月/季 K 写入 {@code stock_bar_agg}；分组规则与 {@link StockMaService} 一致，
 * 锚点 {@code bar_date} 为周期内<strong>最后</strong>一个交易日。
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
    private StockMaService stockMaService;

    @Autowired
    private StockBarAggMapper stockBarAggMapper;

    @Autowired
    private ChartDataChangeNotifier chartDataChangeNotifier;

    /**
     * 全量：weekOption 全部标的，自 {@value #MIN_YEAR} 年起至 {@code endDate}。
     */
    public int syncFull(LocalDate endDate, List<String> codesOverride, boolean notifyChart) {
        List<String> codes = codesOverride != null ? codesOverride : stockMaService.loadTrackedCodes();
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
     * 增量：与 {@link StockMaService#syncIncremental} 相同的日线回溯窗口，只重算可能受影响的周/月/季 K。
     */
    public int syncIncremental(LocalDate endDate, boolean notifyChart) {
        List<String> codes = stockMaService.loadTrackedCodes();
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

    private int recomputeFullForCode(String code, LocalDate endDate) {
        String endStr = endDate.format(Constants.DB_DATE_FORMATTER);
        List<Total> dailies = stockMaService.fetchDailiesAsc(code, MIN_DATE, endDate);
        if (dailies.isEmpty()) {
            return 0;
        }
        List<StockBarAgg> all = new ArrayList<>();
        all.addAll(toAggRows(code, PERIOD_WEEK, PeriodOhlcvAggregator.aggregateWeek(dailies), endStr));
        all.addAll(toAggRows(code, PERIOD_MONTH, PeriodOhlcvAggregator.aggregateMonth(dailies), endStr));
        all.addAll(toAggRows(code, PERIOD_QUARTER, PeriodOhlcvAggregator.aggregateQuarter(dailies), endStr));
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
        List<Total> dailies = stockMaService.fetchDailiesAsc(code, loadStart, endDate);
        if (dailies.isEmpty()) {
            return 0;
        }
        List<PeriodOhlcvBar> bars;
        if (PERIOD_WEEK.equals(periodType)) {
            bars = PeriodOhlcvAggregator.aggregateWeek(dailies);
        } else if (PERIOD_MONTH.equals(periodType)) {
            bars = PeriodOhlcvAggregator.aggregateMonth(dailies);
        } else {
            bars = PeriodOhlcvAggregator.aggregateQuarter(dailies);
        }
        if (bars.isEmpty()) {
            return 0;
        }
        List<StockBarAgg> rows = toAggRows(code, periodType, bars, endStr);
        upsertPartitioned(rows);
        return rows.size();
    }

    private static LocalDate maxDate(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private List<StockBarAgg> toAggRows(String code, String periodType, List<PeriodOhlcvBar> bars, String endStr) {
        return bars.stream()
                .filter(b -> b.getBarDate().compareTo(endStr) <= 0)
                .map(b -> toRow(code, periodType, b))
                .collect(Collectors.toList());
    }

    private static StockBarAgg toRow(String code, String periodType, PeriodOhlcvBar b) {
        return StockBarAgg.builder()
                .code(code)
                .periodType(periodType)
                .barDate(b.getBarDate())
                .firstTradeDate(b.getFirstTradeDate())
                .open(BigDecimal.valueOf(b.getOpen()))
                .high(BigDecimal.valueOf(b.getHigh()))
                .low(BigDecimal.valueOf(b.getLow()))
                .close(BigDecimal.valueOf(b.getClose()))
                .volume(b.getVolume())
                .build();
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
