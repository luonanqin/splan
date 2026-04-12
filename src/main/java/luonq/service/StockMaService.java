package luonq.service;

import bean.MA;
import bean.StockBarAgg;
import bean.StockMa;
import bean.Total;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import luonq.indicator.MovingAverageSma;
import luonq.mapper.MaMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import util.Constants;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 跟踪标的列表见 {@link TrackedStockCodesProvider}；日线由年表拉取；周/月/季均线序列与 {@link StockBarAggService} 写入的 {@code stock_bar_agg} 一致（请先同步聚合表再算 MA）。
 */
@Service
@Slf4j
public class StockMaService {

    public static final String TYPE_DAY = "day";
    public static final String TYPE_WEEK = "week";
    public static final String TYPE_MONTH = "month";
    /** 季线：自然季，{@code date} 与 {@code stock_bar_agg.bar_date} 一致（季首月 1 日） */
    public static final String TYPE_QUARTER = "quarter";

    private static final int MIN_YEAR = 2022;
    private static final LocalDate MIN_DATE = LocalDate.of(MIN_YEAR, 1, 1);
    private static final int UPSERT_BATCH = 500;

    /** 日线增量：在已有 MA 水位线前多取若干日历日，保证 ma60 有足够交易日窗口 */
    private static final int INCR_DAY_LOOKBACK_DAYS = 200;
    /** 周线增量：有历史时只取近若干天日线重算周线（需覆盖约 60 个交易周以稳定 ma60，约 800 日历日） */
    private static final int INCR_WEEK_TAIL_DAYS = 800;
    /** 月线增量：约 6.3 年覆盖 ma60；无历史则从 {@value #MIN_YEAR} 年起 */
    private static final int INCR_MONTH_TAIL_DAYS = 2300;
    /** 季线增量：约 60 季需 15 年；无历史则从 {@value #MIN_YEAR} 年起 */
    private static final int INCR_QUARTER_TAIL_DAYS = 5600;

    @Autowired
    private MaMapper maMapper;

    @Autowired
    private StockDailyDataService stockDailyDataService;

    @Autowired
    private StockBarAggService stockBarAggService;

    @Autowired
    private ChartDataChangeNotifier chartDataChangeNotifier;

    @Autowired
    private TrackedStockCodesProvider trackedStockCodesProvider;

    /**
     * 全量：对跟踪列表中全部股票，从 {@value #MIN_YEAR} 年到 {@code endDate} 重建各周期 type 并 upsert。
     */
    public int syncFull(LocalDate endDate, List<String> codesOverride) {
        List<String> codes = codesOverride != null ? codesOverride : trackedStockCodesProvider.loadAll();
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
        List<String> codes = trackedStockCodesProvider.loadAll();
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
        String endStr = endDate.format(Constants.DB_DATE_FORMATTER);
        List<Total> dailies = stockDailyDataService.fetchDailiesAsc(code, MIN_DATE, endDate);
        if (dailies.isEmpty()) {
            log.debug("no OHLC for code={} through {}", code, endDate);
            return 0;
        }

        List<StockBarAgg> weekBars = stockBarAggService.queryByCodePeriodBetween(code, TYPE_WEEK, null, endStr);
        List<StockBarAgg> monthBars = stockBarAggService.queryByCodePeriodBetween(code, TYPE_MONTH, null, endStr);
        List<StockBarAgg> quarterBars = stockBarAggService.queryByCodePeriodBetween(code, TYPE_QUARTER, null, endStr);

        List<StockMa> all = new ArrayList<>();
        all.addAll(buildTypeRows(code, TYPE_DAY, dailies));
        all.addAll(buildTypeRowsFromAgg(code, TYPE_WEEK, weekBars));
        all.addAll(buildTypeRowsFromAgg(code, TYPE_MONTH, monthBars));
        all.addAll(buildTypeRowsFromAgg(code, TYPE_QUARTER, quarterBars));

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
        if (lastDay != null && lastDay.compareTo(endStr) > 0) {
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
        List<Total> dailies = stockDailyDataService.fetchDailiesAsc(code, loadStart, endDate);
        if (dailies.isEmpty()) {
            return 0;
        }
        List<StockMa> built = buildTypeRows(code, TYPE_DAY, dailies);
        // 与周/月/季一致：窗口内凡 date≤endStr 一律 upsert，便于同日 OHLC 修正时更新已有行而非只追加新日历日
        List<StockMa> toWrite = built.stream()
                .filter(r -> r.getDate().compareTo(endStr) <= 0)
                .collect(Collectors.toList());
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
        String loadStartStr = loadStart.format(Constants.DB_DATE_FORMATTER);
        List<StockBarAgg> weekBars = stockBarAggService.queryByCodePeriodBetween(code, TYPE_WEEK, loadStartStr, endStr);
        if (weekBars.isEmpty()) {
            return 0;
        }
        List<StockMa> built = buildTypeRowsFromAgg(code, TYPE_WEEK, weekBars);
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
        String loadStartStr = loadStart.format(Constants.DB_DATE_FORMATTER);
        List<StockBarAgg> monthBars = stockBarAggService.queryByCodePeriodBetween(code, TYPE_MONTH, loadStartStr, endStr);
        if (monthBars.isEmpty()) {
            return 0;
        }
        List<StockMa> built = buildTypeRowsFromAgg(code, TYPE_MONTH, monthBars);
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
        String loadStartStr = loadStart.format(Constants.DB_DATE_FORMATTER);
        List<StockBarAgg> quarterBars = stockBarAggService.queryByCodePeriodBetween(code, TYPE_QUARTER, loadStartStr, endStr);
        if (quarterBars.isEmpty()) {
            return 0;
        }
        List<StockMa> built = buildTypeRowsFromAgg(code, TYPE_QUARTER, quarterBars);
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
        return buildMaRows(code, type, closes, dates);
    }

    private List<StockMa> buildTypeRowsFromAgg(String code, String type, List<StockBarAgg> barsChrono) {
        List<Double> closes = barsChrono.stream()
                .map(a -> a.getClose() != null ? a.getClose().doubleValue() : 0.0)
                .collect(Collectors.toList());
        List<String> dates = barsChrono.stream().map(StockBarAgg::getBarDate).collect(Collectors.toList());
        return buildMaRows(code, type, closes, dates);
    }

    private static List<StockMa> buildMaRows(String code, String type, List<Double> closes, List<String> dates) {
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
