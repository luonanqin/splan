package luonq.service;

import lombok.extern.slf4j.Slf4j;
import luonq.event.DailyOhlcvUpsertedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 日 K 写入后：周/月/季 K 写入 {@code stock_bar_agg}。
 */
@Component
@Slf4j
public class DailyOhlcvStockBarAggSyncListener {

    private final StockBarAggService stockBarAggService;

    public DailyOhlcvStockBarAggSyncListener(StockBarAggService stockBarAggService) {
        this.stockBarAggService = stockBarAggService;
    }

    @Order(40)
    @EventListener
    public void onDailyOhlcvUpserted(DailyOhlcvUpsertedEvent event) {
        if (event.getRowsUpserted() <= 0) {
            return;
        }
        int rows = stockBarAggService.syncIncrementalChain(event.getAsOfDate());
        log.info(
                "DailyOhlcv listener [stock-bar-agg]: done asOfDate={} dailyRows={} barAggRowsUpserted={}",
                event.getAsOfDate(), event.getRowsUpserted(), rows);
    }
}
