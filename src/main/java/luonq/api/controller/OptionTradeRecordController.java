package luonq.api.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import luonq.api.dto.OptionTradeRecordDto;
import luonq.api.dto.OptionTradeRecordWriteRequest;
import luonq.service.OptionTradeRecordService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@Tag(name = "期权交易记录", description = "按正股代码查询、新增、修改、删除期权单笔交易（买入时间倒序由列表接口保证）")
@RestController
@RequestMapping("/api/stocks/{symbol}/option-trades")
@Slf4j
public class OptionTradeRecordController {

    private final OptionTradeRecordService optionTradeRecordService;

    public OptionTradeRecordController(OptionTradeRecordService optionTradeRecordService) {
        this.optionTradeRecordService = optionTradeRecordService;
    }

    @Operation(summary = "列出某正股下的期权交易", description = "按 buy_date 倒序，不分页。")
    @GetMapping
    public List<OptionTradeRecordDto> list(
            @Parameter(description = "正股代码，如 AAPL") @PathVariable("symbol") String symbol) {
        log.info("api GET /api/stocks/{}/option-trades", symbol);
        return optionTradeRecordService.listByUnderlying(symbol);
    }

    @Operation(summary = "新增期权交易")
    @PostMapping
    public OptionTradeRecordDto create(
            @PathVariable("symbol") String symbol,
            @RequestBody OptionTradeRecordWriteRequest body) {
        log.info("api POST /api/stocks/{}/option-trades", symbol);
        return optionTradeRecordService.create(symbol, body);
    }

    @Operation(
            summary = "更新期权交易",
            description = "须属于路径中的 symbol。请求体为整单（与 GET 列表项结构一致后再改字段）；"
                    + "id 以路径为准。卖出日、卖出价可空；其余字段须合法。支持 camelCase 与 snake_case。"
    )
    @PutMapping("/{id}")
    public OptionTradeRecordDto update(
            @PathVariable("symbol") String symbol,
            @PathVariable("id") long id,
            @RequestBody OptionTradeRecordWriteRequest body) {
        log.info("api PUT /api/stocks/{}/option-trades/{}", symbol, id);
        return optionTradeRecordService.update(symbol, id, body);
    }

    @Operation(summary = "删除期权交易", description = "须属于路径中的 symbol。")
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable("symbol") String symbol,
            @PathVariable("id") long id) {
        log.info("api DELETE /api/stocks/{}/option-trades/{}", symbol, id);
        optionTradeRecordService.delete(symbol, id);
    }
}
