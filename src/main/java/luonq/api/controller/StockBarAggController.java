package luonq.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import luonq.service.StockBarAggService;
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
 * 周/月/季聚合 K 写入 {@code stock_bar_agg}（由日线聚合；与 Massive 日 K 同步后链路行为一致，可单独触发）。
 */
@Tag(name = "周/月/季 K 聚合", description = "从按年日 K 表聚合写入 stock_bar_agg")
@RestController
@RequestMapping("/api/stock-bar-agg")
public class StockBarAggController {

    private final StockBarAggService stockBarAggService;

    public StockBarAggController(StockBarAggService stockBarAggService) {
        this.stockBarAggService = stockBarAggService;
    }

    @Operation(summary = "全量同步", description = "weekOption 全部标的，自 2022-01-01 起至 end（默认今天）重建周/月/季 K 并 upsert。")
    @PostMapping("/sync-full")
    public ResponseEntity<Map<String, Object>> syncFull(
            @Parameter(description = "截止日期 yyyy-MM-dd（含）；不传为今天") @RequestParam(required = false) String end)
            throws Exception {
        LocalDate endDate = StringUtils.isNotBlank(end)
                ? LocalDate.parse(end.trim(), Constants.DB_DATE_FORMATTER)
                : LocalDate.now();
        int rows = stockBarAggService.syncFull(endDate, null, true);
        return ok(rows);
    }

    @Operation(summary = "增量同步", description = "与 MA 相同的日线回溯窗口，重算可能受影响的周/月/季 K。")
    @PostMapping("/sync-incremental")
    public ResponseEntity<Map<String, Object>> syncIncremental(
            @Parameter(description = "截止日期 yyyy-MM-dd（含）；不传为今天") @RequestParam(required = false) String end)
            throws Exception {
        LocalDate endDate = StringUtils.isNotBlank(end)
                ? LocalDate.parse(end.trim(), Constants.DB_DATE_FORMATTER)
                : LocalDate.now();
        int rows = stockBarAggService.syncIncremental(endDate, true);
        return ok(rows);
    }

    @Operation(summary = "单票全量", description = "指定 code，自 2022-01-01 起至 end 重建周/月/季 K。")
    @PostMapping("/sync-code")
    public ResponseEntity<Map<String, Object>> syncCode(
            @Parameter(description = "股票代码", required = true) @RequestParam String code,
            @Parameter(description = "截止日期 yyyy-MM-dd（含）；不传为今天") @RequestParam(required = false) String end)
            throws Exception {
        if (StringUtils.isBlank(code)) {
            return badRequest("code is required");
        }
        LocalDate endDate = StringUtils.isNotBlank(end)
                ? LocalDate.parse(end.trim(), Constants.DB_DATE_FORMATTER)
                : LocalDate.now();
        int rows = stockBarAggService.syncForCode(code.trim(), endDate, true);
        return ok(rows);
    }

    @Operation(summary = "单票增量", description = "指定 code，按增量窗口重算周/月/季 K。")
    @PostMapping("/sync-code-incremental")
    public ResponseEntity<Map<String, Object>> syncCodeIncremental(
            @Parameter(description = "股票代码", required = true) @RequestParam String code,
            @Parameter(description = "截止日期 yyyy-MM-dd（含）；不传为今天") @RequestParam(required = false) String end)
            throws Exception {
        if (StringUtils.isBlank(code)) {
            return badRequest("code is required");
        }
        LocalDate endDate = StringUtils.isNotBlank(end)
                ? LocalDate.parse(end.trim(), Constants.DB_DATE_FORMATTER)
                : LocalDate.now();
        int rows = stockBarAggService.syncForCodeIncremental(code.trim(), endDate, true);
        return ok(rows);
    }

    private static ResponseEntity<Map<String, Object>> ok(int rowsUpserted) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", true);
        body.put("rowsUpserted", rowsUpserted);
        return ResponseEntity.ok(body);
    }

    private static ResponseEntity<Map<String, Object>> badRequest(String message) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("ok", false);
        body.put("message", message);
        return ResponseEntity.badRequest().body(body);
    }
}
