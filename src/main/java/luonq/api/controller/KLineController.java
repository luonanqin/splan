package luonq.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import luonq.api.dto.StockChartResponse;
import luonq.data.ReadFromDB;
import luonq.service.StockChartQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "股票 K 线", description = "标的代码列表与 K 线 OHLCV、指标查询（供前端图表等）")
@RestController
@RequestMapping("/api/stocks")
@Slf4j
public class KLineController {

    private final ReadFromDB readFromDB;
    private final StockChartQueryService stockChartQueryService;

    public KLineController(ReadFromDB readFromDB, StockChartQueryService stockChartQueryService) {
        this.readFromDB = readFromDB;
        this.stockChartQueryService = stockChartQueryService;
    }

    @Operation(summary = "列出全部股票代码", description = "从数据库年表汇总去重后的 ticker 列表，用于下拉、校验等。")
    @GetMapping
    public List<String> listSymbols() {
        List<String> codes = readFromDB.listAllStockCodes();
        log.info("api GET /api/stocks listSymbols count={}", codes.size());
        return codes;
    }

    /**
     * K 线。默认只返回最新 {@value luonq.service.StockChartQueryService#DEFAULT_INITIAL_LIMIT} 根；向左拖时用
     * {@code before=当前最左一根的 time}&amp;{@code limit=100}（或省略 limit 用默认 {@value luonq.service.StockChartQueryService#DEFAULT_BEFORE_CHUNK}）。
     * 若指定 {@code from} 或 {@code to} 任一，则按闭区间全量拉取（不走条数截断与缓存）。
     */
    @Operation(
            summary = "查询 K 线数据",
            description = "默认返回最新若干根；传 before 向左分页。若指定 from 或 to 则闭区间全量拉取（不走缓存）。"
                    + "interval：day/week/month/quarter。"
    )
    @GetMapping("/{symbol}/chart")
    public StockChartResponse chart(
            @Parameter(description = "股票代码，如 AAPL", required = true) @PathVariable String symbol,
            @Parameter(description = "区间起始日期 yyyy-MM-dd；与 to 同时或择一使用则走全量拉取") @RequestParam(required = false) String from,
            @Parameter(description = "区间结束日期 yyyy-MM-dd") @RequestParam(required = false) String to,
            @Parameter(description = "K 线周期：day | week | month | quarter，默认 day") @RequestParam(required = false, defaultValue = "day") String interval,
            @Parameter(description = "返回条数上限；初始默认 300，带 before 时默认 100") @RequestParam(required = false) Integer limit,
            @Parameter(description = "向左加载：仅取严格早于此日期的数据（与 bar.time 一致，yyyy-MM-dd）") @RequestParam(required = false) String before,
            @Parameter(description = "额外均线周期，逗号分隔，如 120,200；库表无列时由后端按收盘价计算（日线会向左补足历史）") @RequestParam(required = false) String computeMaPeriods) {
        log.info(
                "api GET /api/stocks/{}/chart interval={} limit={} before={} from={} to={} computeMaPeriods={}",
                symbol, interval, limit, before, from, to, computeMaPeriods);
        return stockChartQueryService.getChart(symbol, from, to, interval, limit, before, computeMaPeriods);
    }
}
