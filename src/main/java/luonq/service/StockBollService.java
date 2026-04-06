package luonq.service;

import bean.BOLL;
import bean.StockBoll;
import bean.Total;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import luonq.indicator.Bollinger;
import luonq.mapper.BollMapper;
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
 * 从 {@code option/weekOption} 与年表 OHLC 计算 day/week/month/quarter 布林线，写入 {@code boll} 表；算法见 {@link Bollinger#computeSeries}。
 */
@Service
@Slf4j
public class StockBollService {

    private static final int MIN_YEAR = 2022;
    private static final LocalDate MIN_DATE = LocalDate.of(MIN_YEAR, 1, 1);
    private static final int UPSERT_BATCH = 500;

    private static final int INCR_DAY_LOOKBACK_DAYS = 200;
    private static final int INCR_WEEK_TAIL_DAYS = 800;
    private static final int INCR_MONTH_TAIL_DAYS = 2300;
    private static final int INCR_QUARTER_TAIL_DAYS = 5600;

    @Autowired
    private StockMaService stockMaService;

    @Autowired
    private BollMapper bollMapper;

    @Autowired
    private ChartDataChangeNotifier chartDataChangeNotifier;

    public int syncFull(LocalDate endDate, List<String> codesOverride) {
        List<String> codes = codesOverride != null ? codesOverride : stockMaService.loadTrackedCodes();
        if (CollectionUtils.isEmpty(codes)) {
            log.warn("no codes to sync");
            return 0;
        }
        int totalRows = 0;
        for (String code : codes) {
            totalRows += recomputeFullAllTypes(code, endDate);
        }
        log.info("boll syncFull endDate={} codes={} totalRows={}", endDate, codes.size(), totalRows);
        if (totalRows > 0) {
            chartDataChangeNotifier.notifyAllSymbolsChanged();
        }
        return totalRows;
    }

    public int syncIncremental(LocalDate endDate) {
        List<String> codes = stockMaService.loadTrackedCodes();
        if (CollectionUtils.isEmpty(codes)) {
            log.warn("no codes to sync");
            return 0;
        }
        int totalRows = 0;
        for (String code : codes) {
            totalRows += recomputeIncrementalAllTypes(code, endDate);
        }
        log.info("boll syncIncremental endDate={} codes={} totalRows={}", endDate, codes.size(), totalRows);
        if (totalRows > 0) {
            chartDataChangeNotifier.notifyAllSymbolsChanged();
        }
        return totalRows;
    }

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
        List<Total> dailies = stockMaService.fetchDailiesAsc(code, MIN_DATE, endDate);
        if (dailies.isEmpty()) {
            log.debug("no OHLC for boll code={} through {}", code, endDate);
            return 0;
        }

        List<StockBoll> all = new ArrayList<>();
        all.addAll(buildTypeRows(code, StockMaService.TYPE_DAY, dailies));

        List<Total> weekBars = stockMaService.aggregateWeekBars(dailies);
        all.addAll(buildTypeRows(code, StockMaService.TYPE_WEEK, weekBars));

        List<Total> monthBars = stockMaService.aggregateMonthBars(dailies);
        all.addAll(buildTypeRows(code, StockMaService.TYPE_MONTH, monthBars));

        List<Total> quarterBars = stockMaService.aggregateQuarterBars(dailies);
        all.addAll(buildTypeRows(code, StockMaService.TYPE_QUARTER, quarterBars));

        upsertPartitioned(all);
        log.info("boll full upsert code={} dayBars={} weekBars={} monthBars={} quarterBars={} rows={}",
                code, dailies.size(), weekBars.size(), monthBars.size(), quarterBars.size(), all.size());
        return all.size();
    }

    private int recomputeIncrementalAllTypes(String code, LocalDate endDate) {
        String endStr = endDate.format(Constants.DB_DATE_FORMATTER);
        int rows = 0;
        rows += syncDayIncremental(code, endDate, endStr);
        rows += syncWeekIncremental(code, endDate, endStr);
        rows += syncMonthIncremental(code, endDate, endStr);
        rows += syncQuarterIncremental(code, endDate, endStr);
        return rows;
    }

    private int syncDayIncremental(String code, LocalDate endDate, String endStr) {
        String lastDay = bollMapper.selectMaxDate(code, StockMaService.TYPE_DAY);
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
        List<Total> dailies = stockMaService.fetchDailiesAsc(code, loadStart, endDate);
        if (dailies.isEmpty()) {
            return 0;
        }
        List<StockBoll> built = buildTypeRows(code, StockMaService.TYPE_DAY, dailies);
        List<StockBoll> toWrite;
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
        return toWrite.size();
    }

    private int syncWeekIncremental(String code, LocalDate endDate, String endStr) {
        String lastW = bollMapper.selectMaxDate(code, StockMaService.TYPE_WEEK);
        LocalDate loadStart = lastW == null
                ? MIN_DATE
                : maxDate(MIN_DATE, endDate.minusDays(INCR_WEEK_TAIL_DAYS));
        List<Total> dailies = stockMaService.fetchDailiesAsc(code, loadStart, endDate);
        if (dailies.isEmpty()) {
            return 0;
        }
        List<Total> weekBars = stockMaService.aggregateWeekBars(dailies);
        if (weekBars.isEmpty()) {
            return 0;
        }
        List<StockBoll> built = buildTypeRows(code, StockMaService.TYPE_WEEK, weekBars);
        List<StockBoll> toWrite = built.stream()
                .filter(r -> r.getDate().compareTo(endStr) <= 0)
                .collect(Collectors.toList());
        upsertPartitioned(toWrite);
        return toWrite.size();
    }

    private int syncMonthIncremental(String code, LocalDate endDate, String endStr) {
        String lastM = bollMapper.selectMaxDate(code, StockMaService.TYPE_MONTH);
        LocalDate loadStart = lastM == null
                ? MIN_DATE
                : maxDate(MIN_DATE, endDate.minusDays(INCR_MONTH_TAIL_DAYS));
        List<Total> dailies = stockMaService.fetchDailiesAsc(code, loadStart, endDate);
        if (dailies.isEmpty()) {
            return 0;
        }
        List<Total> monthBars = stockMaService.aggregateMonthBars(dailies);
        if (monthBars.isEmpty()) {
            return 0;
        }
        List<StockBoll> built = buildTypeRows(code, StockMaService.TYPE_MONTH, monthBars);
        List<StockBoll> toWrite = built.stream()
                .filter(r -> r.getDate().compareTo(endStr) <= 0)
                .collect(Collectors.toList());
        upsertPartitioned(toWrite);
        return toWrite.size();
    }

    private int syncQuarterIncremental(String code, LocalDate endDate, String endStr) {
        String lastQ = bollMapper.selectMaxDate(code, StockMaService.TYPE_QUARTER);
        LocalDate loadStart = lastQ == null
                ? MIN_DATE
                : maxDate(MIN_DATE, endDate.minusDays(INCR_QUARTER_TAIL_DAYS));
        List<Total> dailies = stockMaService.fetchDailiesAsc(code, loadStart, endDate);
        if (dailies.isEmpty()) {
            return 0;
        }
        List<Total> quarterBars = stockMaService.aggregateQuarterBars(dailies);
        if (quarterBars.isEmpty()) {
            return 0;
        }
        List<StockBoll> built = buildTypeRows(code, StockMaService.TYPE_QUARTER, quarterBars);
        List<StockBoll> toWrite = built.stream()
                .filter(r -> r.getDate().compareTo(endStr) <= 0)
                .collect(Collectors.toList());
        upsertPartitioned(toWrite);
        return toWrite.size();
    }

    private static LocalDate maxDate(LocalDate a, LocalDate b) {
        return a.isAfter(b) ? a : b;
    }

    private void upsertPartitioned(List<StockBoll> rows) {
        if (CollectionUtils.isEmpty(rows)) {
            return;
        }
        for (List<StockBoll> part : Lists.partition(rows, UPSERT_BATCH)) {
            bollMapper.batchUpsertBoll(part);
        }
    }

    private List<StockBoll> buildTypeRows(String code, String type, List<Total> barsChrono) {
        List<Double> closes = barsChrono.stream().map(Total::getClose).collect(Collectors.toList());
        List<String> dates = barsChrono.stream().map(Total::getDate).collect(Collectors.toList());
        List<BOLL> bolls = Bollinger.computeSeries(closes, dates);
        List<StockBoll> rows = new ArrayList<>(bolls.size());
        for (BOLL b : bolls) {
            rows.add(StockBoll.builder()
                    .code(code)
                    .type(type)
                    .date(b.getDate())
                    .md(b.getMd())
                    .mb(b.getMb())
                    .up(b.getUp())
                    .dn(b.getDn())
                    .build());
        }
        return rows;
    }

    /**
     * K 线图表：按 {@code boll} 表读取布林线（与 {@link StockMaService#TYPE_DAY} / week / month / quarter 一致），闭区间、按日期升序。
     */
    public List<StockBoll> queryBollRange(String code, String bollType, String fromInclusive, String toInclusive) {
        if (StringUtils.isBlank(code) || StringUtils.isBlank(bollType)) {
            return Collections.emptyList();
        }
        if (StringUtils.isBlank(fromInclusive) || StringUtils.isBlank(toInclusive)) {
            return Collections.emptyList();
        }
        return bollMapper.selectByCodeTypeBetween(code.trim(), bollType, fromInclusive, toInclusive);
    }
}
