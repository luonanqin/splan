package luonq.service;

import bean.Total;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import luonq.mapper.StockDataMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import luonq.event.DailyOhlcvUpsertedEvent;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import util.Constants;

import java.io.BufferedReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.time.LocalDate;

/**
 * 将 Massive 离线日聚合 CSV（{@code massive/day_aggregates/年/yyyy-MM-dd.csv}）同步到按年分表。
 * CSV 列：ticker,volume,open,close,high,low,window_start,transactions（首行为表头）。
 * <p>
 * 职责：读 CSV、建表、写入日 K。若有新写入行，发布 {@link luonq.event.DailyOhlcvUpsertedEvent}，
 * MA/BOLL/周月季 K 等由 {@code DailyOhlcv*} 系列监听器按顺序处理，与本类解耦。
 */
@Service
@Slf4j
public class MassiveDayAggregateSyncService {

    private static final int BATCH = 500;
    /** 未指定 {@code since} 时，仅处理从该日起（含）的 CSV 文件名日期 */
    public static final int DEFAULT_INCREMENTAL_LOOKBACK_DAYS = 14;
    private static final String DATE_FILE_REGEX = "\\d{4}-\\d{2}-\\d{2}";

    @Value("${massive.day-aggregates.base-path:}")
    private String basePath;

    @Autowired
    private StockDataMapper stockDataMapper;

    @Autowired
    private ApplicationEventPublisher applicationEventPublisher;

    @Autowired
    private TrackedStockCodesProvider trackedStockCodesProvider;

    /**
     * 同步 {@code option/weekOption} 中全部标的；缺表则按 {@link StockDataMapper#createTable} 创建。
     */
    public SyncResult syncAllTrackedCodes() throws Exception {
        Set<String> allow = loadAllowSet();
        if (allow.isEmpty()) {
            return SyncResult.fail("weekOption empty or not found");
        }
        return runSync(allow, null);
    }

    /**
     * 增量：只处理文件名日期 ≥ {@code sinceInclusive} 的 CSV（减少全量扫盘）。
     * {@code sinceInclusive == null} 时：先查各年分表中已存日线的最大 {@code date}，从该日期的下一天起同步；
     * 若无分表或无任何有效日期，则回退为「今天往前 {@value #DEFAULT_INCREMENTAL_LOOKBACK_DAYS} 个日历日」。
     * 若有新写入日 K，会发布 {@link DailyOhlcvUpsertedEvent}，由监听器串联 MA/BOLL/周月季 K 等派生写入。
     */
    public SyncResult syncIncrementalTracked(LocalDate sinceInclusive) throws Exception {
        Set<String> allow = loadAllowSet();
        if (allow.isEmpty()) {
            return SyncResult.fail("weekOption empty or not found");
        }
        LocalDate since = sinceInclusive != null
                ? sinceInclusive
                : resolveDefaultIncrementalSinceFromDatabase();
        return runSync(allow, since);
    }

    /**
     * 仅同步指定股票（ticker 与 CSV 一致，一般大写）。
     */
    public SyncResult syncOneCode(String code) throws Exception {
        if (StringUtils.isBlank(code)) {
            return SyncResult.fail("code is blank");
        }
        Set<String> allow = Collections.singleton(code.trim().toUpperCase(Locale.ROOT));
        return runSync(allow, null);
    }

    /**
     * 单票增量：只处理文件名日期 ≥ {@code sinceInclusive} 的 CSV。
     * {@code sinceInclusive == null} 时：在各年分表中查该票已存日线的最大 {@code date}，从该日期的下一天起同步；
     * 若无分表或该票无任何有效日期，则回退为「今天往前 {@value #DEFAULT_INCREMENTAL_LOOKBACK_DAYS} 个日历日」。
     */
    public SyncResult syncOneCodeIncremental(String code, LocalDate sinceInclusive) throws Exception {
        if (StringUtils.isBlank(code)) {
            return SyncResult.fail("code is blank");
        }
        String normalized = code.trim().toUpperCase(Locale.ROOT);
        Set<String> allow = Collections.singleton(normalized);
        LocalDate since = sinceInclusive != null
                ? sinceInclusive
                : resolveDefaultIncrementalSinceFromDatabaseForCode(normalized);
        return runSync(allow, since);
    }

    private Set<String> loadAllowSet() {
        List<String> raw = trackedStockCodesProvider.loadAll();
        if (CollectionUtils.isEmpty(raw)) {
            return Collections.emptySet();
        }
        return raw.stream()
                .map(s -> s.trim().toUpperCase(Locale.ROOT))
                .filter(s -> !s.isEmpty())
                .collect(Collectors.toCollection(HashSet::new));
    }

    /**
     * 全市场增量未指定 since 时：取库中所有年分表的最大交易日，从下一天开始；无法得到有效日期时回退为固定回溯天数。
     */
    private LocalDate resolveDefaultIncrementalSinceFromDatabase() {
        List<String> years = stockDataMapper.listStockDataYears();
        if (CollectionUtils.isEmpty(years)) {
            LocalDate fb = LocalDate.now().minusDays(DEFAULT_INCREMENTAL_LOOKBACK_DAYS);
            log.info(
                    "incremental since: no yyyy stock tables, fallback {} calendar days -> {}",
                    DEFAULT_INCREMENTAL_LOOKBACK_DAYS,
                    fb);
            return fb;
        }
        String maxStr = null;
        for (String y : years) {
            String m = stockDataMapper.selectMaxDateForYear(y);
            if (StringUtils.isBlank(m) || !m.matches(DATE_FILE_REGEX)) {
                continue;
            }
            if (maxStr == null || m.compareTo(maxStr) > 0) {
                maxStr = m;
            }
        }
        if (maxStr == null) {
            LocalDate fb = LocalDate.now().minusDays(DEFAULT_INCREMENTAL_LOOKBACK_DAYS);
            log.info(
                    "incremental since: no valid max date in stock tables, fallback {} calendar days -> {}",
                    DEFAULT_INCREMENTAL_LOOKBACK_DAYS,
                    fb);
            return fb;
        }
        LocalDate next = LocalDate.parse(maxStr, Constants.DB_DATE_FORMATTER).plusDays(1);
        log.info("incremental since: latest daily date in DB {} -> sync from {} inclusive", maxStr, next);
        return next;
    }

    /**
     * 单票增量未指定 since 时：取该 code 在各年分表中的最大交易日，从下一天开始；无法得到有效日期时回退为固定回溯天数。
     */
    private LocalDate resolveDefaultIncrementalSinceFromDatabaseForCode(String normalizedCode) {
        List<String> years = stockDataMapper.listStockDataYears();
        if (CollectionUtils.isEmpty(years)) {
            LocalDate fb = LocalDate.now().minusDays(DEFAULT_INCREMENTAL_LOOKBACK_DAYS);
            log.info(
                    "incremental since [{}]: no yyyy stock tables, fallback {} calendar days -> {}",
                    normalizedCode,
                    DEFAULT_INCREMENTAL_LOOKBACK_DAYS,
                    fb);
            return fb;
        }
        String maxStr = null;
        for (String y : years) {
            String m = stockDataMapper.selectMaxDateForCodeInYear(y, normalizedCode);
            if (StringUtils.isBlank(m) || !m.matches(DATE_FILE_REGEX)) {
                continue;
            }
            if (maxStr == null || m.compareTo(maxStr) > 0) {
                maxStr = m;
            }
        }
        if (maxStr == null) {
            LocalDate fb = LocalDate.now().minusDays(DEFAULT_INCREMENTAL_LOOKBACK_DAYS);
            log.info(
                    "incremental since [{}]: no valid max date in stock tables, fallback {} calendar days -> {}",
                    normalizedCode,
                    DEFAULT_INCREMENTAL_LOOKBACK_DAYS,
                    fb);
            return fb;
        }
        LocalDate next = LocalDate.parse(maxStr, Constants.DB_DATE_FORMATTER).plusDays(1);
        log.info(
                "incremental since [{}]: latest daily date in DB {} -> sync from {} inclusive",
                normalizedCode,
                maxStr,
                next);
        return next;
    }

    /**
     * @param sinceInclusiveOrNull 非空时仅处理 CSV 文件名日期 ≥ 该日；全量同步传 {@code null}
     */
    private SyncResult runSync(Set<String> allowTickers, LocalDate sinceInclusiveOrNull) throws Exception {
        if (StringUtils.isBlank(basePath)) {
            return SyncResult.fail("massive.day-aggregates.base-path is not configured");
        }
        Path root = Paths.get(basePath.trim());
        if (!Files.isDirectory(root)) {
            return SyncResult.fail("base path is not a directory: " + root);
        }

        final String sinceStr = sinceInclusiveOrNull == null
                ? null
                : sinceInclusiveOrNull.format(Constants.DB_DATE_FORMATTER);

        List<Path> yearDirs = listYearDirectories(root);
        if (yearDirs.isEmpty()) {
            return SyncResult.fail("no year subdirectories under " + root);
        }

        int files = 0;
        long rows = 0;
        /** 本次 run 中实际写入行数 &gt;0 的 CSV 文件名日期之最大 yyyy-MM-dd，供派生周月季 K 与 asOfDate 对齐（避免仅用 LocalDate.now() 早于已写入末日时漏日线） */
        String maxWrittenTradeDateStr = null;
        Set<String> ensuredYears = new HashSet<>();
        for (Path yearDir : yearDirs) {
            String year = yearDir.getFileName().toString();

            List<Path> csvFiles;
            try (Stream<Path> s = Files.list(yearDir)) {
                csvFiles = s.filter(p -> Files.isRegularFile(p))
                        .filter(p -> p.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".csv"))
                        .sorted(Comparator.comparing(p -> p.getFileName().toString()))
                        .collect(Collectors.toList());
            }

            for (Path csv : csvFiles) {
                String name = csv.getFileName().toString();
                String dateStr = name.substring(0, name.length() - 4);
                if (!dateStr.matches(DATE_FILE_REGEX)) {
                    log.warn("skip file (name not yyyy-MM-dd.csv): {}", csv);
                    continue;
                }
                if (sinceStr != null && dateStr.compareTo(sinceStr) < 0) {
                    continue;
                }
                String dbYear = dateStr.substring(0, 4);
                if (!dbYear.equals(year)) {
                    log.warn("csv {} date year differs from parent dir {}, using {}", csv, year, dbYear);
                }
                if (ensuredYears.add(dbYear)) {
                    ensureYearTable(dbYear);
                }
                int n = parseFileAndUpsert(csv, dateStr, dbYear, allowTickers);
                if (n > 0) {
                    files++;
                    rows += n;
                    if (maxWrittenTradeDateStr == null || dateStr.compareTo(maxWrittenTradeDateStr) > 0) {
                        maxWrittenTradeDateStr = dateStr;
                    }
                }
            }
        }

        LocalDate asOfDateForDerivatives = LocalDate.now();
        if (maxWrittenTradeDateStr != null) {
            LocalDate lastWritten = LocalDate.parse(maxWrittenTradeDateStr, Constants.DB_DATE_FORMATTER);
            if (lastWritten.isAfter(asOfDateForDerivatives)) {
                asOfDateForDerivatives = lastWritten;
            }
        }
        log.info(
                "massive day_aggregates sync done since={} filesTouched={} rowsUpserted={} allowSize={}",
                sinceStr, files, rows, allowTickers.size());
        if (rows > 0) {
            String singleOrNull = allowTickers.size() == 1 ? allowTickers.iterator().next() : null;
            applicationEventPublisher.publishEvent(new DailyOhlcvUpsertedEvent(asOfDateForDerivatives, rows, singleOrNull));
        }
        return SyncResult.ok(files, rows, sinceStr);
    }

    private static List<Path> listYearDirectories(Path root) throws Exception {
        try (Stream<Path> s = Files.list(root)) {
            return s.filter(Files::isDirectory)
                    .filter(p -> p.getFileName().toString().matches("\\d{4}"))
                    .sorted(Comparator.comparing(p -> Integer.parseInt(p.getFileName().toString())))
                    .collect(Collectors.toList());
        }
    }

    private void ensureYearTable(String year) {
        if (StringUtils.isBlank(stockDataMapper.showTables(year))) {
            stockDataMapper.createTable(year);
            log.info("created year table {}", year);
        }
    }

    private int parseFileAndUpsert(Path csv, String dateStr, String dbYear, Set<String> allowTickers) throws Exception {
        List<Total> buf = new ArrayList<>();
        int[] headerIx = null;

        try (BufferedReader br = Files.newBufferedReader(csv, StandardCharsets.UTF_8)) {
            String line;
            while ((line = br.readLine()) != null) {
                if (StringUtils.isBlank(line)) {
                    continue;
                }
                if (headerIx == null) {
                    headerIx = parseHeader(line);
                    continue;
                }
                Total row = parseDataLine(line, headerIx, dateStr, allowTickers);
                if (row != null) {
                    buf.add(row);
                }
            }
        }

        if (buf.isEmpty()) {
            return 0;
        }
        for (List<Total> part : Lists.partition(buf, BATCH)) {
            stockDataMapper.batchUpsertOhlcvDaily(part, dbYear);
        }
        return buf.size();
    }

    private static int[] parseHeader(String headerLine) {
        String[] cols = headerLine.split(",", -1);
        Map<String, Integer> map = new HashMap<>();
        for (int i = 0; i < cols.length; i++) {
            map.put(cols[i].trim().toLowerCase(Locale.ROOT), i);
        }
        int[] ix = new int[6];
        ix[0] = requiredCol(map, "ticker");
        ix[1] = requiredCol(map, "volume");
        ix[2] = requiredCol(map, "open");
        ix[3] = requiredCol(map, "close");
        ix[4] = requiredCol(map, "high");
        ix[5] = requiredCol(map, "low");
        return ix;
    }

    private static int requiredCol(Map<String, Integer> map, String name) {
        Integer i = map.get(name);
        if (i == null) {
            throw new IllegalArgumentException("CSV missing column: " + name);
        }
        return i;
    }

    private static Total parseDataLine(String line, int[] ix, String dateStr, Set<String> allowTickers) {
        String[] p = line.split(",", -1);
        if (p.length <= ix[5]) {
            return null;
        }
        String ticker = p[ix[0]].trim().toUpperCase(Locale.ROOT);
        if (!allowTickers.contains(ticker)) {
            return null;
        }
        try {
            BigDecimal volume = new BigDecimal(p[ix[1]].trim());
            double open = Double.parseDouble(p[ix[2]].trim());
            double close = Double.parseDouble(p[ix[3]].trim());
            double high = Double.parseDouble(p[ix[4]].trim());
            double low = Double.parseDouble(p[ix[5]].trim());
            return buildOhlcvTotal(ticker, dateStr, open, close, high, low, volume);
        } catch (Exception e) {
            log.warn("skip bad row ticker={} date={} msg={}", ticker, dateStr, e.getMessage());
            return null;
        }
    }

    private static Total buildOhlcvTotal(String code, String date, double open, double close, double high, double low, BigDecimal volume) {
        Total t = new Total();
        t.setCode(code);
        t.setDate(date);
        t.setOpen(open);
        t.setClose(close);
        t.setHigh(high);
        t.setLow(low);
        t.setVolume(volume);
        t.setOpenTrade(0);
        t.setOpenTradeTime("");
        t.setF1minAvgPrice(0);
        t.setF1minVolume(0);
        t.setMd(0);
        t.setMb(0);
        t.setUp(0);
        t.setDn(0);
        t.setOpenMd(0);
        t.setOpenMb(0);
        t.setOpenUp(0);
        t.setOpenDn(0);
        t.setMa5(0);
        t.setMa10(0);
        t.setMa20(0);
        t.setMa30(0);
        t.setMa60(0);
        return t;
    }

    public static final class SyncResult {
        public final boolean ok;
        public final String message;
        public final int filesTouched;
        public final long rowsUpserted;
        /** 增量同步时实际使用的起始日期（yyyy-MM-dd，含）；全量同步为 null */
        public final String effectiveSinceInclusive;

        private SyncResult(
                boolean ok,
                String message,
                int filesTouched,
                long rowsUpserted,
                String effectiveSinceInclusive) {
            this.ok = ok;
            this.message = message;
            this.filesTouched = filesTouched;
            this.rowsUpserted = rowsUpserted;
            this.effectiveSinceInclusive = effectiveSinceInclusive;
        }

        static SyncResult ok(int files, long rows, String effectiveSinceInclusive) {
            return new SyncResult(true, null, files, rows, effectiveSinceInclusive);
        }

        static SyncResult fail(String msg) {
            return new SyncResult(false, msg, 0, 0, null);
        }
    }
}
