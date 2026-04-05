package luonq.api.controller;

import bean.Total;
import luonq.api.dto.CandleBarDto;
import luonq.api.dto.StockChartResponse;
import luonq.data.ReadFromDB;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/stocks")
public class KLineController {

    private final ReadFromDB readFromDB;

    public KLineController(ReadFromDB readFromDB) {
        this.readFromDB = readFromDB;
    }

    @GetMapping
    public List<String> listSymbols() {
        return readFromDB.listAllStockCodes();
    }

    /**
     * 日 K。可选 {@code from} / {@code to}（yyyy-MM-dd，闭区间）。
     */
    @GetMapping("/{symbol}/chart")
    public StockChartResponse dailyChart(
            @PathVariable String symbol,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to) {
        List<Total> rows = readFromDB.queryDailyByCode(symbol, from, to);
        if (rows.isEmpty()) {
            List<Total> any = readFromDB.queryDailyByCode(symbol, null, null);
            if (any.isEmpty()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No K-line data for symbol: " + symbol);
            }
            return StockChartResponse.builder()
                    .symbol(symbol)
                    .bars(Collections.emptyList())
                    .indicators(Collections.emptyMap())
                    .build();
        }

        List<CandleBarDto> bars = new ArrayList<>(rows.size());
        for (Total t : rows) {
            bars.add(CandleBarDto.builder()
                    .time(t.getDate())
                    .open(t.getOpen())
                    .high(t.getHigh())
                    .low(t.getLow())
                    .close(t.getClose())
                    .volume(t.getVolume() != null ? t.getVolume().doubleValue() : null)
                    .build());
        }

        List<StockChartResponse.LinePointDto> ma20 = ma20FromTotals(rows);
        if (ma20.isEmpty()) {
            ma20 = buildMa(bars, 20);
        }
        Map<String, List<StockChartResponse.LinePointDto>> indicators = new HashMap<>();
        indicators.put("ma20", ma20);

        return StockChartResponse.builder()
                .symbol(symbol)
                .bars(bars)
                .indicators(indicators)
                .build();
    }

    private static List<StockChartResponse.LinePointDto> ma20FromTotals(List<Total> rows) {
        List<StockChartResponse.LinePointDto> out = new ArrayList<>();
        for (Total t : rows) {
            if (t.getMa20() == 0.0) {
                continue;
            }
            out.add(StockChartResponse.LinePointDto.builder()
                    .time(t.getDate())
                    .value(Math.round(t.getMa20() * 100.0) / 100.0)
                    .build());
        }
        return out;
    }

    private static List<StockChartResponse.LinePointDto> buildMa(List<CandleBarDto> bars, int period) {
        if (bars.size() < period) {
            return Collections.emptyList();
        }
        List<StockChartResponse.LinePointDto> out = new ArrayList<>();
        for (int i = period - 1; i < bars.size(); i++) {
            double sum = 0;
            for (int j = 0; j < period; j++) {
                sum += bars.get(i - j).getClose();
            }
            double v = Math.round(sum * 100.0 / period) / 100.0;
            out.add(StockChartResponse.LinePointDto.builder()
                    .time(bars.get(i).getTime())
                    .value(v)
                    .build());
        }
        return out;
    }
}
