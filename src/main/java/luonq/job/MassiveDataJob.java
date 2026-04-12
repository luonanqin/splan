package luonq.job;

import com.xxl.job.core.context.XxlJobHelper;
import com.xxl.job.core.handler.annotation.XxlJob;
import lombok.extern.slf4j.Slf4j;
import luonq.service.MassiveDayAggregateSyncService;
import luonq.service.MassiveDayAggregateSyncService.SyncResult;
import luonq.service.MassiveDayFlatFileDownloadService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import util.Constants;

import java.time.LocalDate;

/**
 * Massive 日 K 离线：Flat Files 下载（S3）与本地 CSV 入库增量同步。
 * <p>
 * 下载任务 XXL-JOB：JobHandler = {@code massiveDayFlatfilesDownload.job}，Cron 例如每天凌晨执行。
 * 可选参数：单行 {@code yyyy-MM-dd}，表示只下载该日及之后的交易日（补数）；留空则处理全部「≤ 今天」的交易日。
 * <p>
 * 入库任务 XXL-JOB：JobHandler = {@code massiveDayAggregatesIncrementalSync.job}，建议在下载任务之后执行（或错开几分钟）。
 * 将 {@code massive.day-aggregates.base-path} 下已存在的 CSV 增量写入按年分表，逻辑与
 * {@code POST /api/daily-bar/sync-massive-incremental} 一致。可选参数：单行 {@code yyyy-MM-dd} 作为起始日（含）；
 * 留空则按库中最新日线日期的下一天起算（无数据时回退 14 天）。
 */
@Slf4j
@Component
public class MassiveDataJob {

    @Autowired
    private MassiveDayFlatFileDownloadService massiveDayFlatFileDownloadService;

    @Autowired
    private MassiveDayAggregateSyncService massiveDayAggregateSyncService;

    @XxlJob("massiveDayFlatfilesDownload.job")
    public void massiveDayFlatfilesDownload() throws Exception {
        String param = XxlJobHelper.getJobParam();
        log.info("massiveDayFlatfilesDownload.job start, param={}", param);
        XxlJobHelper.log("massiveDayFlatfilesDownload.job start, param=" + param);

        LocalDate since = null;
        if (StringUtils.isNotBlank(param)) {
            String p = param.trim();
            try {
                since = LocalDate.parse(p);
            } catch (Exception e) {
                String msg = "job param must be empty or yyyy-MM-dd, got: " + p;
                log.warn(msg);
                XxlJobHelper.log(msg);
                throw new IllegalArgumentException(msg);
            }
        }

        MassiveDayFlatFileDownloadService.DownloadResult r =
                massiveDayFlatFileDownloadService.downloadAllMissingTradeDays(since);

        String summary = String.format(
                "done downloaded=%d skippedCsv=%d skippedFuture=%d failed=%d",
                r.getDownloaded(), r.getSkippedExistingCsv(), r.getSkippedFuture(), r.getFailed());
        log.info("massiveDayFlatfilesDownload.job {}", summary);
        XxlJobHelper.log(summary);
    }

    @XxlJob("massiveDayAggregatesIncrementalSync.job")
    public void massiveDayAggregatesIncrementalSync() throws Exception {
        String param = XxlJobHelper.getJobParam();
        log.info("massiveDayAggregatesIncrementalSync.job start, param={}", param);
        XxlJobHelper.log("massiveDayAggregatesIncrementalSync.job start, param=" + param);

        LocalDate since = null;
        if (StringUtils.isNotBlank(param)) {
            String p = param.trim();
            try {
                since = LocalDate.parse(p, Constants.DB_DATE_FORMATTER);
            } catch (Exception e) {
                String msg = "job param must be empty or yyyy-MM-dd, got: " + p;
                log.warn(msg);
                XxlJobHelper.log(msg);
                throw new IllegalArgumentException(msg);
            }
        }

        SyncResult r = massiveDayAggregateSyncService.syncIncrementalTracked(since);
        if (!r.ok) {
            String msg = StringUtils.defaultIfBlank(r.message, "syncIncrementalTracked failed");
            log.warn("massiveDayAggregatesIncrementalSync.job {}", msg);
            XxlJobHelper.log(msg);
            throw new IllegalStateException(msg);
        }

        String summary = String.format(
                "done filesTouched=%d rowsUpserted=%d effectiveSince=%s",
                r.filesTouched,
                r.rowsUpserted,
                r.effectiveSinceInclusive != null ? r.effectiveSinceInclusive : "n/a");
        log.info("massiveDayAggregatesIncrementalSync.job {}", summary);
        XxlJobHelper.log(summary);
    }
}
