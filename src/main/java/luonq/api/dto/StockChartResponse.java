package luonq.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * JSON shape aligned with splan_frontend {@code StockChartBundle}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockChartResponse {
    private String symbol;
    private List<CandleBarDto> bars;
    @Builder.Default
    private Map<String, List<LinePointDto>> indicators = Collections.emptyMap();

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class LinePointDto {
        private String time;
        private double value;
    }
}
