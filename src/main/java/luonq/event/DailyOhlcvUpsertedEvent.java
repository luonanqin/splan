package luonq.event;

import java.time.LocalDate;

/**
 * 日 K（按年表 OHLCV）批量写入完成后发布；多个监听器按 {@code @Order} 依次处理图表失效、MA、BOLL、周月季 K 等。
 */
public final class DailyOhlcvUpsertedEvent {

    private final LocalDate asOfDate;
    private final long rowsUpserted;
    /** 非空表示仅单票受影响（图表逐票失效）；{@code null} 表示全市场 */
    private final String singleSymbolOrNull;

    public DailyOhlcvUpsertedEvent(LocalDate asOfDate, long rowsUpserted, String singleSymbolOrNull) {
        this.asOfDate = asOfDate;
        this.rowsUpserted = rowsUpserted;
        this.singleSymbolOrNull = singleSymbolOrNull;
    }

    public LocalDate getAsOfDate() {
        return asOfDate;
    }

    public long getRowsUpserted() {
        return rowsUpserted;
    }

    public String getSingleSymbolOrNull() {
        return singleSymbolOrNull;
    }
}
