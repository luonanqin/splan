package luonq.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import luonq.service.MassiveDayAggregateSyncService;
import luonq.service.MassiveDayAggregateSyncService.SyncResult;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import util.Constants;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 将 Massive 离线日聚合 CSV 同步到按年分表（仅 OHLCV，已存在行只覆盖 OHLCV）。
 */
@Tag(name = "日 K 线 CSV 同步", description = "将 Massive 离线日聚合 CSV 导入按年分表；仅更新 OHLCV 字段")
@RestController
@RequestMapping("/api/daily-bar")
public class DailyBarSyncController {

    private final MassiveDayAggregateSyncService massiveDayAggregateSyncService;

    public DailyBarSyncController(MassiveDayAggregateSyncService massiveDayAggregateSyncService) {
        this.massiveDayAggregateSyncService = massiveDayAggregateSyncService;
    }

    /**
     * 全量：仅同步 {@code option/weekOption} 中的 ticker；遍历 {@code massive.day-aggregates.base-path} 下各年目录中所有 {@code yyyy-MM-dd.csv}。
     */
    @Operation(summary = "Massive 日聚合全量同步", description = "仅处理 weekOption 中的标的；扫描 base-path 下各年目录中所有 yyyy-MM-dd.csv。有日 K 写入时发布事件，由监听器处理图表缓存、MA、BOLL、stock_bar_agg；派生结果见日志（DailyOhlcv listener [...]）。")
    @PostMapping("/sync-massive-full")
    public ResponseEntity<Map<String, Object>> syncMassiveFull() throws Exception {
        SyncResult r = massiveDayAggregateSyncService.syncAllTrackedCodes();
        return toResponse(r);
    }

    @Operation(
            summary = "Massive 日聚合增量同步（全市场）",
            description = "只处理文件名日期 ≥ since 的 CSV；未传 since 时默认最近 "
                    + MassiveDayAggregateSyncService.DEFAULT_INCREMENTAL_LOOKBACK_DAYS
                    + " 个日历日。有日 K 写入时发布事件触发派生逻辑，结果见日志。"
    )
    @PostMapping("/sync-massive-incremental")
    public ResponseEntity<Map<String, Object>> syncMassiveIncremental(
            @Parameter(description = "起始日期 yyyy-MM-dd（含）；不传则默认今天往前 " + MassiveDayAggregateSyncService.DEFAULT_INCREMENTAL_LOOKBACK_DAYS + " 天") @RequestParam(required = false) String since)
            throws Exception {
        LocalDate sinceDate = null;
        if (StringUtils.isNotBlank(since)) {
            sinceDate = LocalDate.parse(since.trim(), Constants.DB_DATE_FORMATTER);
        }
        SyncResult r = massiveDayAggregateSyncService.syncIncrementalTracked(sinceDate);
        return toResponse(r);
    }

    /**
     * 单票：仍扫描全部 CSV，只写入与 {@code code} 匹配的行（ticker 大写比对）。
     */
    @Operation(summary = "Massive 日聚合单票全量同步", description = "遍历全部 CSV 文件，仅 upsert 与 code 匹配的行。有日 K 写入时发布事件触发派生逻辑，结果见日志。")
    @PostMapping("/sync-massive-code")
    public ResponseEntity<Map<String, Object>> syncMassiveCode(
            @Parameter(description = "股票代码，必填", required = true) @RequestParam String code) throws Exception {
        if (StringUtils.isBlank(code)) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("ok", false);
            err.put("message", "code is required");
            return ResponseEntity.badRequest().body(err);
        }
        SyncResult r = massiveDayAggregateSyncService.syncOneCode(code.trim());
        return toResponse(r);
    }

    @Operation(
            summary = "Massive 日聚合单票增量同步",
            description = "只处理文件名日期 ≥ since 的 CSV；未传 since 时默认今天往前 "
                    + MassiveDayAggregateSyncService.DEFAULT_INCREMENTAL_LOOKBACK_DAYS
                    + " 天。有日 K 写入时发布事件触发派生逻辑，结果见日志。"
    )
    @PostMapping("/sync-massive-code-incremental")
    public ResponseEntity<Map<String, Object>> syncMassiveCodeIncremental(
            @Parameter(description = "股票代码，必填", required = true) @RequestParam String code,
            @Parameter(description = "起始日期 yyyy-MM-dd（含）；不传则默认最近 " + MassiveDayAggregateSyncService.DEFAULT_INCREMENTAL_LOOKBACK_DAYS + " 天") @RequestParam(required = false) String since)
            throws Exception {
        if (StringUtils.isBlank(code)) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("ok", false);
            err.put("message", "code is required");
            return ResponseEntity.badRequest().body(err);
        }
        LocalDate sinceDate = null;
        if (StringUtils.isNotBlank(since)) {
            sinceDate = LocalDate.parse(since.trim(), Constants.DB_DATE_FORMATTER);
        }
        SyncResult r = massiveDayAggregateSyncService.syncOneCodeIncremental(code.trim(), sinceDate);
        return toResponse(r);
    }

    private static ResponseEntity<Map<String, Object>> toResponse(SyncResult r) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", r.ok);
        if (r.message != null) {
            body.put("message", r.message);
        }
        body.put("filesTouched", r.filesTouched);
        body.put("rowsUpserted", r.rowsUpserted);
        if (!r.ok) {
            return ResponseEntity.badRequest().body(body);
        }
        return ResponseEntity.ok(body);
    }
}
