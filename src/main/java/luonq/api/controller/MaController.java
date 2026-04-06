package luonq.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import luonq.service.StockMaService;
import org.apache.commons.lang3.StringUtils;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 调试 MA 同步：数据来自年表 OHLC，标的列表来自 {@code classpath:option/weekOption}。
 */
@Tag(name = "均线 MA 同步", description = "从年表 OHLC 计算日/周/月/季 SMA 并写入 ma 表；标的列表来自 classpath:option/weekOption")
@RestController
@RequestMapping("/api/ma")
public class MaController {

    private final StockMaService stockMaService;

    public MaController(StockMaService stockMaService) {
        this.stockMaService = stockMaService;
    }

    /**
     * 全量：weekOption 全部股票，日线截断到 {@code endDate}（默认今天）。
     */
    @Operation(summary = "MA 全量同步（全市场）", description = "对 weekOption 中全部股票从配置起始年重算至 endDate，写入 ma 表。")
    @PostMapping("/sync-full")
    public ResponseEntity<Map<String, Object>> syncFull(
            @Parameter(description = "计算截止日期，默认当天，格式 yyyy-MM-dd") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        int rows = stockMaService.syncFull(end, null);
        return ok("sync-full", end, rows);
    }

    /**
     * 增量：按 {@code ma} 水位线只算新日线 + 近窗周/月线，适合每日跑批。
     */
    @Operation(summary = "MA 增量同步（全市场）", description = "按 ma 表已有最大日期只补算新数据，适合每日定时任务。")
    @PostMapping("/sync-incremental")
    public ResponseEntity<Map<String, Object>> syncIncremental(
            @Parameter(description = "计算截止日期，默认当天") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        int rows = stockMaService.syncIncremental(end);
        return ok("sync-incremental", end, rows);
    }

    /**
     * 单票全量重算（补数、调试）。
     */
    @Operation(summary = "MA 单票全量重算", description = "指定股票从起始年重算至 endDate，用于补数或调试。")
    @PostMapping("/sync-code")
    public ResponseEntity<Map<String, Object>> syncCode(
            @Parameter(description = "股票代码，必填", required = true) @RequestParam String code,
            @Parameter(description = "计算截止日期，默认当天") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (StringUtils.isBlank(code)) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("ok", false);
            err.put("message", "code is required");
            return ResponseEntity.badRequest().body(err);
        }
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        int rows = stockMaService.syncForCode(code.trim(), end);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("action", "sync-code");
        body.put("code", code.trim());
        body.put("endDate", end.toString());
        body.put("rowsUpserted", rows);
        return ResponseEntity.ok(body);
    }

    /**
     * 单票增量（与列表增量逻辑一致）。
     */
    @Operation(summary = "MA 单票增量同步", description = "与全市场增量逻辑一致，仅针对一只股票。")
    @PostMapping("/sync-code-incremental")
    public ResponseEntity<Map<String, Object>> syncCodeIncremental(
            @Parameter(description = "股票代码，必填", required = true) @RequestParam String code,
            @Parameter(description = "计算截止日期，默认当天") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        if (StringUtils.isBlank(code)) {
            Map<String, Object> err = new LinkedHashMap<>();
            err.put("ok", false);
            err.put("message", "code is required");
            return ResponseEntity.badRequest().body(err);
        }
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        int rows = stockMaService.syncForCodeIncremental(code.trim(), end);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("action", "sync-code-incremental");
        body.put("code", code.trim());
        body.put("endDate", end.toString());
        body.put("rowsUpserted", rows);
        return ResponseEntity.ok(body);
    }

    private static ResponseEntity<Map<String, Object>> ok(String action, LocalDate end, int rows) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("action", action);
        body.put("endDate", end.toString());
        body.put("rowsUpserted", rows);
        return ResponseEntity.ok(body);
    }
}
