package bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 由日线聚合得到的周期 K（周/月/季），与 {@code stock_bar_agg} 语义一致：
 * {@code barDate} 为周期内<strong>最后</strong>一个交易日，{@code firstTradeDate} 为周期内首个交易日。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PeriodOhlcvBar {

    private String code;
    private String firstTradeDate;
    /** 周期锚点：最后一个交易日，对应 stock_bar_agg.bar_date */
    private String barDate;
    private double open;
    private double high;
    private double low;
    private double close;
    private long volume;
}
