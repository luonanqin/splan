package luonq.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import luonq.service.StockBollService;
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
 * 调试 BOLL 同步：OHLC 与标的列表与 {@link MaController} 相同；计算复用 {@link luonq.indicator.Bollinger}。
 */
@Tag(name = "布林带 BOLL 同步", description = "基于年表 OHLC 计算布林带并写入 boll 表；标的列表与 MA 模块一致")
@RestController
@RequestMapping("/api/boll")
public class BollingController {

    private final StockBollService stockBollService;

    public BollingController(StockBollService stockBollService) {
        this.stockBollService = stockBollService;
    }

    @Operation(summary = "BOLL 全量同步（全市场）", description = "对 weekOption 中全部股票从起始年重算至 endDate。")
    @PostMapping("/sync-full")
    public ResponseEntity<Map<String, Object>> syncFull(
            @Parameter(description = "计算截止日期，默认当天") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        int rows = stockBollService.syncFull(end, null);
        return ok("sync-full", end, rows);
    }

    @Operation(summary = "BOLL 增量同步（全市场）", description = "按已有水位线只补算新数据，适合每日跑批。")
    @PostMapping("/sync-incremental")
    public ResponseEntity<Map<String, Object>> syncIncremental(
            @Parameter(description = "计算截止日期，默认当天") @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        int rows = stockBollService.syncIncremental(end);
        return ok("sync-incremental", end, rows);
    }

    @Operation(summary = "BOLL 单票全量重算", description = "指定股票全量重算至 endDate。")
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
        int rows = stockBollService.syncForCode(code.trim(), end);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("action", "sync-code");
        body.put("code", code.trim());
        body.put("endDate", end.toString());
        body.put("rowsUpserted", rows);
        return ResponseEntity.ok(body);
    }

    @Operation(summary = "BOLL 单票增量同步", description = "与全市场增量逻辑一致，仅针对一只股票。")
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
        int rows = stockBollService.syncForCodeIncremental(code.trim(), end);
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
