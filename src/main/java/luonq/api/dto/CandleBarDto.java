package luonq.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CandleBarDto {
    /** yyyy-MM-dd for daily bars (Lightweight Charts business day string). */
    private String time;
    private double open;
    private double high;
    private double low;
    private double close;
    private Double volume;
}
