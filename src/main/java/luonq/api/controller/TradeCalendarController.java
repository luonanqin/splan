package luonq.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import luonq.service.TradeCalendarHolidayService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 供 Postman 等工具手动触发交易日历同步。
 */
@Tag(name = "交易日历", description = "从外部接口同步美股休市日等到 trade_calendar 表")
@RestController
@RequestMapping("/api/trade-calendar")
public class TradeCalendarController {

    private final TradeCalendarHolidayService tradeCalendarHolidayService;

    public TradeCalendarController(TradeCalendarHolidayService tradeCalendarHolidayService) {
        this.tradeCalendarHolidayService = tradeCalendarHolidayService;
    }

    /**
     * 拉取 Massive/Polygon 市场假期并写入当年 {@code trade_calendar}（逻辑见 {@link TradeCalendarHolidayService}）。
     * <p>Postman: {@code POST} {@code /api/trade-calendar/sync-us-holidays}，无需 body。</p>
     */
    @Operation(
            summary = "同步美股休市日历",
            description = "拉取 Massive/Polygon 市场假期并写入当年 trade_calendar；无需请求体。"
    )
    @PostMapping("/sync-us-holidays")
    public ResponseEntity<Map<String, Object>> syncUsHolidays() {
        int year = LocalDate.now().getYear();
        try {
            tradeCalendarHolidayService.syncCurrentYearUsEquityCalendar();
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", true);
            body.put("year", year);
            body.put("message", "trade_calendar synced");
            return ResponseEntity.ok(body);
        } catch (IllegalStateException e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", false);
            body.put("year", year);
            body.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(body);
        } catch (Exception e) {
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("ok", false);
            body.put("year", year);
            body.put("message", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
        }
    }
}
