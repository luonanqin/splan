package bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * 对应表 {@code stock_bar_agg}：周/月/季聚合 K（由日线聚合写入）。
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockBarAgg {

    private Long id;
    private String code;
    /** week / month / quarter */
    private String periodType;
    /** 锚点：周线为该 ISO 周在 trade_calendar 中首个交易日（无则周一）；月/季见 {@link luonq.aggregate.PeriodOhlcvAggregator} */
    private String barDate;
    /** 周期内首个交易日 */
    private String firstTradeDate;
    /** 本周期 K 已聚合到的最后交易日（增量时仅从此日之后拉日线合并 OHLC） */
    private String lastTradeDate;
    private BigDecimal open;
    private BigDecimal high;
    private BigDecimal low;
    private BigDecimal close;
    private long volume;
}
