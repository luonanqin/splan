package luonq.service;

import bean.StockBoll;
import bean.StockMa;
import bean.Total;
import com.google.common.base.Stopwatch;
import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import lombok.extern.slf4j.Slf4j;
import luonq.api.dto.CandleBarDto;
import luonq.api.dto.StockChartResponse;
import luonq.data.ReadFromDB;
import luonq.event.ChartSourceDataChangedEvent;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import java.util.stream.Collectors;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * K 线查询：默认只取最新若干根 bar；支持 {@code before} 向左分页；Guava 缓存热点请求。
 */
@Service
@Slf4j
public class StockChartQueryService {

    public static final int DEFAULT_INITIAL_LIMIT = 300;
    public static final int DEFAULT_BEFORE_CHUNK = 100;

    private final ReadFromDB readFromDB;
    private final StockMaService stockMaService;
    private final StockBollService stockBollService;

    /** access：热点；write：兜底，避免遗漏失效通知时长期脏读 */
    private final Cache<String, StockChartResponse> chartCache = CacheBuilder.newBuilder()
            .maximumSize(4000)
            .expireAfterAccess(15, TimeUnit.MINUTES)
            .expireAfterWrite(6, TimeUnit.HOURS)
            .build();

    public StockChartQueryService(
            ReadFromDB readFromDB,
            StockMaService stockMaService,
            StockBollService stockBollService) {
        this.readFromDB = readFromDB;
        this.stockMaService = stockMaService;
        this.stockBollService = stockBollService;
    }

    /**
     * @param from/to 若任一有值，则按闭区间全量拉取（不截断、不走缓存），兼容旧用法
     * @param limit    返回的 K 线根数上限；初始默认 {@value #DEFAULT_INITIAL_LIMIT}；带 {@code before} 时默认 {@value #DEFAULT_BEFORE_CHUNK}
     * @param before   仅取严格早于该日期的数据（yyyy-MM-dd），用于向左加载
     * @param computeMaPeriodsRaw 逗号分隔的额外均线周期（如 {@code 120,200}），库表无列时由收盘价现算；日线会向左补足历史以便窗口正确
     */
    public StockChartResponse getChart(
            String symbol,
            String from,
            String to,
            String interval,
            Integer limit,
            String before,
            String computeMaPeriodsRaw) {
        Stopwatch sw = Stopwatch.createStarted();
        String intervalToken = normalizeChartInterval(interval);
        List<Integer> computeMaPeriods = parseComputeMaPeriods(computeMaPeriodsRaw);
        boolean fullRange = (from != null && !from.trim().isEmpty())
                || (to != null && !to.trim().isEmpty());

        log.info(
                "chart.query start symbol={} interval={} fullRange={} limitParam={} before={} from={} to={} computeMaPeriods={}",
                symbol, intervalToken, fullRange, limit, before, from, to, computeMaPeriods);

        if (fullRange) {
            StockChartResponse r = buildFullRange(symbol, from, to, intervalToken, computeMaPeriods);
            log.info(
                    "chart.query fullRange done symbol={} bars={} hasMoreOlder={} took={}",
                    symbol, r.getBars() != null ? r.getBars().size() : 0, r.getHasMoreOlder(), sw.stop());
            return r;
        }

        int barLimit = resolveBarLimit(limit, before);
        String cacheKey = cacheKey(symbol, intervalToken, barLimit, before, to, computeMaPeriods);
        StockChartResponse cached = chartCache.getIfPresent(cacheKey);
        if (cached != null) {
            log.info(
                    "chart.query cacheHit symbol={} interval={} barLimit={} before={} bars={} took={}",
                    symbol, intervalToken, barLimit, before,
                    cached.getBars() != null ? cached.getBars().size() : 0, sw.stop());
            return cached;
        }

        StockChartResponse built = buildPaginated(symbol, to, intervalToken, barLimit, before, computeMaPeriods);
        chartCache.put(cacheKey, built);
        log.info(
                "chart.query miss symbol={} interval={} barLimit={} before={} bars={} hasMoreOlder={} took={}",
                symbol, intervalToken, barLimit, before,
                built.getBars() != null ? built.getBars().size() : 0, built.getHasMoreOlder(), sw.stop());
        return built;
    }

    /**
     * 同步监听；当前 Spring 版本下 {@code TransactionalEventListener} 与 {@code EventListener} 元注解不兼容会导致启动失败。
     * 失效缓存为轻量操作；若需在事务提交后执行，可升级 Spring Boot 或改为异步 + 延迟。
     */
    @EventListener
    public void onChartSourceDataChanged(ChartSourceDataChangedEvent event) {
        String sym = event.getSymbol();
        if (sym == null || sym.isEmpty() || sym.trim().isEmpty()) {
            chartCache.invalidateAll();
            log.info("chart.cache invalidated all (source data changed)");
            return;
        }
        int removed = invalidateChartCacheForSymbol(sym);
        log.info("chart.cache invalidated symbol={} keysRemoved={}", sym.trim(), removed);
    }

    /**
     * 使某标的在 Guava 中的全部查询键失效（与 symbol 大小写无关）。
     */
    public int invalidateChartCacheForSymbol(String symbol) {
        if (StringUtils.isBlank(symbol)) {
            return 0;
        }
        String needle = symbol.trim();
        List<String> keys = new ArrayList<>();
        for (String k : chartCache.asMap().keySet()) {
            int sep = k.indexOf('\0');
            if (sep <= 0) {
                continue;
            }
            if (needle.equalsIgnoreCase(k.substring(0, sep))) {
                keys.add(k);
            }
        }
        for (String k : keys) {
            chartCache.invalidate(k);
        }
        return keys.size();
    }

    /** 全量清空图表缓存（例如批量导入、全市场同步）。 */
    public void invalidateAllChartCache() {
        chartCache.invalidateAll();
        log.info("chart.cache invalidated all (manual)");
    }

    /**
     * 解析 {@code computeMaPeriods} 查询串：逗号分隔正整数，范围 [2,500]，最多 5 个，升序去重。
     */
    public static List<Integer> parseComputeMaPeriods(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return Collections.emptyList();
        }
        TreeSet<Integer> set = new TreeSet<>();
        for (String part : raw.split(",")) {
            String s = part.trim();
            if (s.isEmpty()) {
                continue;
            }
            try {
                int p = Integer.parseInt(s);
                if (p >= 2 && p <= 500) {
                    set.add(p);
                }
            } catch (NumberFormatException ignored) {
                /* skip */
            }
        }
        if (set.isEmpty()) {
            return Collections.emptyList();
        }
        List<Integer> out = new ArrayList<>(set);
        if (out.size() > 5) {
            return out.subList(0, 5);
        }
        return out;
    }

    private static String cacheKey(
            String symbol,
            String interval,
            int barLimit,
            String before,
            String to,
            List<Integer> computeMaPeriods) {
        String maPart = computeMaPeriods == null || computeMaPeriods.isEmpty()
                ? ""
                : computeMaPeriods.stream().map(String::valueOf).collect(Collectors.joining(","));
        return symbol + "\0" + interval + "\0" + barLimit + "\0" + String.valueOf(before) + "\0" + String.valueOf(to) + "\0" + maPart;
    }

    private static int resolveBarLimit(Integer limit, String before) {
        boolean beforeMode = before != null && !before.trim().isEmpty();
        if (beforeMode) {
            if (limit != null && limit > 0) {
                return limit;
            }
            return DEFAULT_BEFORE_CHUNK;
        }
        if (limit != null && limit > 0) {
            return limit;
        }
        return DEFAULT_INITIAL_LIMIT;
    }

    private StockChartResponse buildFullRange(
            String symbol, String from, String to, String intervalToken, List<Integer> computeMaPeriods) {
        List<Total> rows = readFromDB.queryDailyByCode(symbol, from, to);
        if (rows.isEmpty()) {
            List<Total> any = readFromDB.queryDailyByCode(symbol, null, null);
            if (any.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No K-line data for symbol: " + symbol);
            }
            return StockChartResponse.builder()
                    .symbol(symbol)
                    .interval(intervalToken)
                    .bars(Collections.emptyList())
                    .indicators(Collections.emptyMap())
                    .hasMoreOlder(false)
                    .build();
        }
        List<Total> source = rows;
        if ("week".equals(intervalToken)) {
            source = stockMaService.aggregateWeekBars(rows);
        } else if ("month".equals(intervalToken)) {
            source = stockMaService.aggregateMonthBars(rows);
        } else if ("quarter".equals(intervalToken)) {
            source = stockMaService.aggregateQuarterBars(rows);
        }
        return assembleResponse(symbol, intervalToken, source, false, computeMaPeriods);
    }

    private StockChartResponse buildPaginated(
            String symbol,
            String to,
            String intervalToken,
            int barLimit,
            String before,
            List<Integer> computeMaPeriods) {
        List<Total> dailies;
        if (before != null && !before.trim().isEmpty()) {
            int dailyNeed = dailyFetchCountForInterval(barLimit, intervalToken);
            dailies = readFromDB.queryDailyBeforeExclusive(symbol, before.trim(), dailyNeed);
        } else {
            int dailyNeed = dailyFetchCountForInterval(barLimit, intervalToken);
            dailies = readFromDB.queryLatestDaily(symbol, dailyNeed, to);
        }

        if (dailies.isEmpty()) {
            List<Total> any = readFromDB.queryDailyByCode(symbol, null, null);
            if (any.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No K-line data for symbol: " + symbol);
            }
            return StockChartResponse.builder()
                    .symbol(symbol)
                    .interval(intervalToken)
                    .bars(Collections.emptyList())
                    .indicators(Collections.emptyMap())
                    .hasMoreOlder(false)
                    .build();
        }

        List<Total> source = dailies;
        if ("week".equals(intervalToken)) {
            source = stockMaService.aggregateWeekBars(dailies);
        } else if ("month".equals(intervalToken)) {
            source = stockMaService.aggregateMonthBars(dailies);
        } else if ("quarter".equals(intervalToken)) {
            source = stockMaService.aggregateQuarterBars(dailies);
        }

        if (before != null && !before.trim().isEmpty()) {
            if (source.size() > barLimit) {
                source = new ArrayList<>(source.subList(source.size() - barLimit, source.size()));
            }
        } else {
            if (source.size() > barLimit) {
                source = new ArrayList<>(source.subList(source.size() - barLimit, source.size()));
            }
        }

        boolean hasMoreOlder = computeHasMoreOlder(symbol, source);
        return assembleResponse(symbol, intervalToken, source, hasMoreOlder, computeMaPeriods);
    }

    private boolean computeHasMoreOlder(String symbol, List<Total> source) {
        if (source.isEmpty()) {
            return false;
        }
        String oldest = source.get(0).getDate();
        List<Total> probe = readFromDB.queryDailyBeforeExclusive(symbol, oldest, 1);
        return probe != null && !probe.isEmpty();
    }

    private StockChartResponse assembleResponse(
            String symbol,
            String intervalToken,
            List<Total> rows,
            boolean hasMoreOlder,
            List<Integer> computeMaPeriods) {
        List<CandleBarDto> bars = new ArrayList<>(rows.size());
        for (Total t : rows) {
            bars.add(candleBarFromTotal(t));
        }

        Map<String, List<StockChartResponse.LinePointDto>> indicators = new HashMap<>();
        fillMaIndicators(symbol, intervalToken, rows, bars, indicators);
        fillBollIndicators(symbol, intervalToken, rows, bars, indicators);
        appendComputedMaPeriods(symbol, intervalToken, rows, bars, indicators, computeMaPeriods);

        return StockChartResponse.builder()
                .symbol(symbol)
                .interval(intervalToken)
                .bars(bars)
                .indicators(indicators)
                .hasMoreOlder(hasMoreOlder)
                .build();
    }

    private static CandleBarDto candleBarFromTotal(Total t) {
        return CandleBarDto.builder()
                .time(t.getDate())
                .open(t.getOpen())
                .high(t.getHigh())
                .low(t.getLow())
                .close(t.getClose())
                .volume(t.getVolume() != null ? t.getVolume().doubleValue() : null)
                .build();
    }

    /**
     * 库表无列的长周期均线：按收盘价 SMA 写入 {@code ma{n}}；日线向左补足 {@code maxPeriod-1} 根以便窗口正确；其它周期仅在当前返回的 bar 序列上计算（左缘可能不足）。
     */
    private void appendComputedMaPeriods(
            String symbol,
            String intervalToken,
            List<Total> rows,
            List<CandleBarDto> bars,
            Map<String, List<StockChartResponse.LinePointDto>> indicators,
            List<Integer> computeMaPeriods) {
        if (computeMaPeriods == null || computeMaPeriods.isEmpty() || rows.isEmpty() || bars.isEmpty()) {
            return;
        }
        Set<String> rowDates = rows.stream().map(Total::getDate).collect(Collectors.toCollection(HashSet::new));
        int maxP = computeMaPeriods.stream().mapToInt(Integer::intValue).max().orElse(0);
        if (maxP <= 1) {
            return;
        }
        if ("day".equals(intervalToken)) {
            List<Total> warmup = readFromDB.queryDailyBeforeExclusive(symbol, rows.get(0).getDate(), maxP - 1);
            List<CandleBarDto> extended = new ArrayList<>(warmup.size() + bars.size());
            for (Total t : warmup) {
                extended.add(candleBarFromTotal(t));
            }
            extended.addAll(bars);
            for (int p : computeMaPeriods) {
                List<StockChartResponse.LinePointDto> full = buildMa(extended, p);
                indicators.put("ma" + p, filterLinePointsByTimes(full, rowDates));
            }
        } else {
            for (int p : computeMaPeriods) {
                indicators.put("ma" + p, buildMa(bars, p));
            }
        }
    }

    private static List<StockChartResponse.LinePointDto> filterLinePointsByTimes(
            List<StockChartResponse.LinePointDto> points, Set<String> times) {
        List<StockChartResponse.LinePointDto> out = new ArrayList<>();
        for (StockChartResponse.LinePointDto pt : points) {
            if (times.contains(pt.getTime())) {
                out.add(pt);
            }
        }
        return out;
    }

    /**
     * 为聚合周期准备足够日线根数，再截取目标 K 线根数。
     */
    private static int dailyFetchCountForInterval(int barLimit, String interval) {
        int n = barLimit + 80;
        switch (interval) {
            case "week":
                return Math.min(25000, Math.max(n * 6, 2000));
            case "month":
                return Math.min(50000, Math.max(n * 35, 8000));
            case "quarter":
                return Math.min(80000, Math.max(n * 95, 20000));
            default:
                return Math.min(25000, n);
        }
    }

    public static String normalizeChartInterval(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return "day";
        }
        String s = raw.trim().toLowerCase().replace(" ", "");
        switch (s) {
            case "day":
            case "d":
            case "1d":
            case "daily":
                return "day";
            case "week":
            case "w":
            case "1w":
            case "weekly":
                return "week";
            case "month":
            case "m":
            case "1mo":
            case "1mth":
            case "monthly":
                return "month";
            case "quarter":
            case "q":
            case "1q":
            case "3mo":
            case "quarterly":
                return "quarter";
            default:
                return "day";
        }
    }

    /**
     * 均线取自 {@code ma} 表（按 K 线周期 type 对齐）；若无数据则按收盘价递推 {@link #buildMa}。total 年表仅含 OHLCV，不再读其 ma 字段。
     */
    private void fillMaIndicators(
            String symbol,
            String intervalToken,
            List<Total> rows,
            List<CandleBarDto> bars,
            Map<String, List<StockChartResponse.LinePointDto>> indicators) {
        int[] periods = {5, 10, 20, 30, 60};
        Map<String, StockMa> byDate = Collections.emptyMap();
        if (!rows.isEmpty()) {
            String from = rows.get(0).getDate();
            String to = rows.get(rows.size() - 1).getDate();
            String maType = maTypeForInterval(intervalToken);
            List<StockMa> maRows = stockMaService.queryMaRange(symbol, maType, from, to);
            if (maRows != null && !maRows.isEmpty()) {
                byDate = new HashMap<>(maRows.size() * 2);
                for (StockMa sm : maRows) {
                    byDate.put(sm.getDate(), sm);
                }
            }
        }
        for (int p : periods) {
            List<StockChartResponse.LinePointDto> fromDb = maFromStockMaMap(rows, byDate, p);
            if (fromDb.isEmpty()) {
                fromDb = buildMa(bars, p);
            }
            indicators.put("ma" + p, fromDb);
        }
    }

    private static String maTypeForInterval(String intervalToken) {
        switch (intervalToken) {
            case "week":
                return StockMaService.TYPE_WEEK;
            case "month":
                return StockMaService.TYPE_MONTH;
            case "quarter":
                return StockMaService.TYPE_QUARTER;
            default:
                return StockMaService.TYPE_DAY;
        }
    }

    private static List<StockChartResponse.LinePointDto> maFromStockMaMap(
            List<Total> rows, Map<String, StockMa> byDate, int period) {
        if (byDate.isEmpty()) {
            return Collections.emptyList();
        }
        List<StockChartResponse.LinePointDto> out = new ArrayList<>();
        for (Total t : rows) {
            StockMa m = byDate.get(t.getDate());
            if (m == null) {
                continue;
            }
            double v = maFieldForPeriod(m, period);
            if (v == 0.0) {
                continue;
            }
            out.add(StockChartResponse.LinePointDto.builder()
                    .time(t.getDate())
                    .value(Math.round(v * 100.0) / 100.0)
                    .build());
        }
        return out;
    }

    private static double maFieldForPeriod(StockMa m, int period) {
        switch (period) {
            case 5:
                return m.getMa5();
            case 10:
                return m.getMa10();
            case 20:
                return m.getMa20();
            case 30:
                return m.getMa30();
            case 60:
                return m.getMa60();
            default:
                return 0.0;
        }
    }

    /**
     * 布林线取自 {@code boll} 表（按 K 线周期 type 对齐）；若任一带为空则按收盘价递推 {@link #fillBollFromBarsOldestFirst}。total 年表不再读其 boll 字段。
     */
    private void fillBollIndicators(
            String symbol,
            String intervalToken,
            List<Total> rows,
            List<CandleBarDto> bars,
            Map<String, List<StockChartResponse.LinePointDto>> indicators) {
        Map<String, StockBoll> byDate = Collections.emptyMap();
        if (!rows.isEmpty()) {
            String from = rows.get(0).getDate();
            String to = rows.get(rows.size() - 1).getDate();
            String bollType = maTypeForInterval(intervalToken);
            List<StockBoll> bollRows = stockBollService.queryBollRange(symbol, bollType, from, to);
            if (bollRows != null && !bollRows.isEmpty()) {
                byDate = new HashMap<>(bollRows.size() * 2);
                for (StockBoll b : bollRows) {
                    byDate.put(b.getDate(), b);
                }
            }
        }
        List<StockChartResponse.LinePointDto> up = bollScalarFromStockBoll(rows, byDate, StockBoll::getUp);
        List<StockChartResponse.LinePointDto> mb = bollScalarFromStockBoll(rows, byDate, StockBoll::getMb);
        List<StockChartResponse.LinePointDto> dn = bollScalarFromStockBoll(rows, byDate, StockBoll::getDn);
        if (!up.isEmpty() && !mb.isEmpty() && !dn.isEmpty()) {
            indicators.put("bollUpper", up);
            indicators.put("bollMiddle", mb);
            indicators.put("bollLower", dn);
            return;
        }
        fillBollFromBarsOldestFirst(bars, indicators);
    }

    private static List<StockChartResponse.LinePointDto> bollScalarFromStockBoll(
            List<Total> rows,
            Map<String, StockBoll> byDate,
            Function<StockBoll, Double> getter) {
        if (byDate.isEmpty()) {
            return Collections.emptyList();
        }
        List<StockChartResponse.LinePointDto> out = new ArrayList<>();
        for (Total t : rows) {
            StockBoll b = byDate.get(t.getDate());
            if (b == null) {
                continue;
            }
            double v = getter.apply(b);
            if (v == 0.0) {
                continue;
            }
            out.add(StockChartResponse.LinePointDto.builder()
                    .time(t.getDate())
                    .value(Math.round(v * 1000.0) / 1000.0)
                    .build());
        }
        return out;
    }

    /**
     * 与 {@link luonq.indicator.Bollinger} 注释一致：20 周期收盘均线为中轨，标准差×2 为上下轨（中轨 2 位 HALF_UP，上下轨 3 位 DOWN）。
     */
    private static void fillBollFromBarsOldestFirst(
            List<CandleBarDto> bars,
            Map<String, List<StockChartResponse.LinePointDto>> indicators) {
        int n = bars.size();
        if (n < 20) {
            return;
        }
        List<StockChartResponse.LinePointDto> upList = new ArrayList<>();
        List<StockChartResponse.LinePointDto> mbList = new ArrayList<>();
        List<StockChartResponse.LinePointDto> dnList = new ArrayList<>();
        for (int i = 19; i < n; i++) {
            double sum = 0;
            for (int j = 0; j < 20; j++) {
                sum += bars.get(i - 19 + j).getClose();
            }
            BigDecimal mbBd = BigDecimal.valueOf(sum)
                    .divide(BigDecimal.valueOf(20), 2, RoundingMode.HALF_UP);
            double mb = mbBd.doubleValue();
            double varSum = 0;
            for (int j = 0; j < 20; j++) {
                double c = bars.get(i - 19 + j).getClose();
                double d = c - mb;
                varSum += d * d;
            }
            double md = Math.sqrt(varSum / 20.0);
            BigDecimal md2 = BigDecimal.valueOf(md).multiply(BigDecimal.valueOf(2));
            double up = mbBd.add(md2).setScale(3, RoundingMode.DOWN).doubleValue();
            double dn = mbBd.subtract(md2).setScale(3, RoundingMode.DOWN).doubleValue();
            String t = bars.get(i).getTime();
            upList.add(StockChartResponse.LinePointDto.builder().time(t).value(up).build());
            mbList.add(StockChartResponse.LinePointDto.builder().time(t).value(mbBd.doubleValue()).build());
            dnList.add(StockChartResponse.LinePointDto.builder().time(t).value(dn).build());
        }
        indicators.put("bollUpper", upList);
        indicators.put("bollMiddle", mbList);
        indicators.put("bollLower", dnList);
    }

    private static List<StockChartResponse.LinePointDto> buildMa(List<CandleBarDto> bars, int period) {
        if (bars.size() < period) {
            return Collections.emptyList();
        }
        List<StockChartResponse.LinePointDto> out = new ArrayList<>();
        for (int i = period - 1; i < bars.size(); i++) {
            double sum = 0;
            for (int j = 0; j < period; j++) {
                sum += bars.get(i - j).getClose();
            }
            double v = Math.round(sum * 100.0 / period) / 100.0;
            out.add(StockChartResponse.LinePointDto.builder()
                    .time(bars.get(i).getTime())
                    .value(v)
                    .build());
        }
        return out;
    }
}
