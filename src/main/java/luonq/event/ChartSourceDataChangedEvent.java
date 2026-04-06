package luonq.event;

/**
 * K 线或图表依赖的指标（ma/boll 等）在库中发生变更后发布，用于使 {@link luonq.service.StockChartQueryService} 的本地缓存失效。
 */
public final class ChartSourceDataChangedEvent {

    /** 受影响股票代码；{@code null} 或空串表示全市场/无法逐票列举时需整表清空缓存 */
    private final String symbol;

    public ChartSourceDataChangedEvent(String symbol) {
        this.symbol = symbol;
    }

    public String getSymbol() {
        return symbol;
    }
}
