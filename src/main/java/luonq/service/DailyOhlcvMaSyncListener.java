package luonq.service;

import lombok.extern.slf4j.Slf4j;
import luonq.event.DailyOhlcvUpsertedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 日 K 写入后：MA 表增量重算。
 */
@Component
@Slf4j
public class DailyOhlcvMaSyncListener {

    private final StockMaService stockMaService;

    public DailyOhlcvMaSyncListener(StockMaService stockMaService) {
        this.stockMaService = stockMaService;
    }

    @Order(20)
    @EventListener
    public void onDailyOhlcvUpserted(DailyOhlcvUpsertedEvent event) {
        if (event.getRowsUpserted() <= 0) {
            return;
        }
        int rows = stockMaService.syncIncremental(event.getAsOfDate());
        log.info(
                "DailyOhlcv listener [ma]: done asOfDate={} dailyRows={} maRowsUpserted={}",
                event.getAsOfDate(), event.getRowsUpserted(), rows);
    }
}
