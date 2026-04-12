package luonq.service;

import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import luonq.mapper.TradeCalendarMapper;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;

import java.io.InputStream;
import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.zip.GZIPInputStream;

/**
 * 从 Massive Flat Files（S3 兼容端点）按 {@code trade_calendar} 交易日下载日聚合 gzip，
 * 解压为 CSV，目录布局与 {@code splan_data/massive_day_aggregates.py} 一致。
 * <p>
 * 对象键：{@code us_stocks_sip/day_aggs_v1/{year}/{MM}/{yyyy-MM-dd}.csv.gz}
 */
@Service
@Slf4j
public class MassiveDayFlatFileDownloadService {

    private static final String KEY_PREFIX = "us_stocks_sip/day_aggs_v1";
    private static final DateTimeFormatter MONTH = DateTimeFormatter.ofPattern("MM");
    private static final DateTimeFormatter ISO = DateTimeFormatter.ISO_LOCAL_DATE;

    @Autowired
    private TradeCalendarMapper tradeCalendarMapper;

    @Value("${massive.flatfiles.s3-endpoint:https://files.massive.com}")
    private String s3Endpoint;

    @Value("${massive.flatfiles.bucket:flatfiles}")
    private String bucket;

    @Value("${massive.flatfiles.access-key:}")
    private String accessKey;

    @Value("${massive.flatfiles.secret-key:}")
    private String secretKey;

    /** 解压后 CSV 根目录：{@code .../massive/day_aggregates/{year}/yyyy-MM-dd.csv} */
    @Value("${massive.day-aggregates.base-path:}")
    private String csvBasePath;

    private final AtomicReference<S3Client> s3ClientHolder = new AtomicReference<>();

    @Getter
    public static final class DownloadResult {
        private int downloaded;
        private int skippedExistingCsv;
        private int skippedFuture;
        private int failed;

        public void incDownloaded() {
            downloaded++;
        }

        public void incSkippedExistingCsv() {
            skippedExistingCsv++;
        }

        public void incSkippedFuture() {
            skippedFuture++;
        }

        public void incFailed() {
            failed++;
        }
    }

    /**
     * 下载所有「交易日 ≤ 今天」且本地尚无 CSV 的日文件。
     *
     * @param sinceInclusive 若非空，仅处理 {@code date >= sinceInclusive} 的交易日（便于补数）
     */
    public DownloadResult downloadAllMissingTradeDays(LocalDate sinceInclusive) throws Exception {
        DownloadResult result = new DownloadResult();
        if (StringUtils.isBlank(csvBasePath)) {
            log.warn("massive.day-aggregates.base-path empty, skip flat file download");
            return result;
        }
        if (StringUtils.isBlank(accessKey) || StringUtils.isBlank(secretKey)) {
            log.warn("massive.flatfiles access-key/secret-key empty, skip S3 download (set MASSIVE_FLATFILES_ACCESS_KEY / MASSIVE_FLATFILES_SECRET_KEY)");
            return result;
        }

        S3Client s3 = getOrCreateS3Client();
        Path csvRoot = Paths.get(csvBasePath).normalize();
        Path massiveParent = csvRoot.getParent();
        if (massiveParent == null) {
            log.warn("cannot resolve parent of base-path: {}", csvBasePath);
            return result;
        }
        Path zipRoot = massiveParent.resolve("zip").resolve("day_aggregates");

        List<String> rawDates = tradeCalendarMapper.listAllTradeDatesOrderByDate();
        if (rawDates == null || rawDates.isEmpty()) {
            log.warn("trade_calendar has no rows");
            return result;
        }

        LocalDate today = LocalDate.now();
        List<LocalDate> dates = new ArrayList<>();
        for (String s : rawDates) {
            if (StringUtils.isBlank(s)) {
                continue;
            }
            LocalDate d = LocalDate.parse(s.trim(), ISO);
            if (d.isAfter(today)) {
                result.incSkippedFuture();
                continue;
            }
            if (sinceInclusive != null && d.isBefore(sinceInclusive)) {
                continue;
            }
            dates.add(d);
        }

        for (LocalDate d : dates) {
            try {
                boolean did = downloadAndGunzip(s3, d, zipRoot, csvRoot);
                if (did) {
                    result.incDownloaded();
                } else {
                    result.incSkippedExistingCsv();
                }
            } catch (Exception e) {
                result.incFailed();
                log.warn("flat file failed date={} : {}", d, e.getMessage());
            }
        }
        log.info("Massive flat files: downloaded={}, skippedCsv={}, skippedFuture={}, failed={}",
                result.getDownloaded(), result.getSkippedExistingCsv(), result.getSkippedFuture(), result.getFailed());
        return result;
    }

    /**
     * @return true 若本次新下载并解压；false 若因 CSV 已存在跳过
     */
    private boolean downloadAndGunzip(S3Client s3, LocalDate d, Path zipRoot, Path csvRoot) throws Exception {
        String objectKey = toObjectKey(d);
        String gzName = objectKey.substring(objectKey.lastIndexOf('/') + 1);
        if (!gzName.endsWith(".gz")) {
            throw new IllegalArgumentException("unexpected key (not .gz): " + objectKey);
        }
        String csvName = gzName.substring(0, gzName.length() - 3);

        Path csvYearDir = csvRoot.resolve(String.valueOf(d.getYear()));
        Path csvPath = csvYearDir.resolve(csvName);

        if (Files.exists(csvPath)) {
            log.debug("Skip (csv exists): {}", csvPath);
            return false;
        }

        Files.createDirectories(zipRoot);
        Files.createDirectories(csvYearDir);

        Path gzPath = zipRoot.resolve(gzName);

        if (!Files.exists(gzPath)) {
            log.info("Downloading '{}' -> {}", objectKey, gzPath);
            GetObjectRequest req = GetObjectRequest.builder()
                    .bucket(bucket)
                    .key(objectKey)
                    .build();
            s3.getObject(req, gzPath);
        } else {
            log.info("Reuse gzip: {}", gzPath);
        }

        try (InputStream raw = Files.newInputStream(gzPath);
             GZIPInputStream in = new GZIPInputStream(raw)) {
            Files.copy(in, csvPath);
        }
        log.info("Extracted: {}", csvPath);
        return true;
    }

    private static String toObjectKey(LocalDate d) {
        String mm = d.format(MONTH);
        String ds = d.format(ISO);
        return KEY_PREFIX + "/" + d.getYear() + "/" + mm + "/" + ds + ".csv.gz";
    }

    private S3Client getOrCreateS3Client() {
        return s3ClientHolder.updateAndGet(prev -> {
            if (prev != null) {
                return prev;
            }
            return S3Client.builder()
                    .endpointOverride(URI.create(s3Endpoint))
                    .region(Region.US_EAST_1)
                    .credentialsProvider(StaticCredentialsProvider.create(
                            AwsBasicCredentials.create(accessKey.trim(), secretKey.trim())))
                    .serviceConfiguration(S3Configuration.builder()
                            .pathStyleAccessEnabled(true)
                            .build())
                    .build();
        });
    }
}
