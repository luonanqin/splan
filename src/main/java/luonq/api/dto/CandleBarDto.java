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
    /** 周期 K：周期内首个交易日（十字线展示）；锚点 {@link #time} 仍为周期最后交易日。 */
    private String firstTradeDate;
    private double open;
    private double high;
    private double low;
    private double close;
    private Double volume;
    /** 相对上一根 K 线收盘的涨跌额；当前返回窗口内第一根无「上一根」时为 null */
    private Double change;
    /** 相对上一根收盘的涨跌幅（%）；第一根为 null */
    private Double changePercent;
}
