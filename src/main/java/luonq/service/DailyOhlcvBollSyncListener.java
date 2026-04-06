package luonq.service;

import lombok.extern.slf4j.Slf4j;
import luonq.event.DailyOhlcvUpsertedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 日 K 写入后：BOLL 表增量重算。
 */
@Component
@Slf4j
public class DailyOhlcvBollSyncListener {

    private final StockBollService stockBollService;

    public DailyOhlcvBollSyncListener(StockBollService stockBollService) {
        this.stockBollService = stockBollService;
    }

    @Order(30)
    @EventListener
    public void onDailyOhlcvUpserted(DailyOhlcvUpsertedEvent event) {
        if (event.getRowsUpserted() <= 0) {
            return;
        }
        int rows = stockBollService.syncIncremental(event.getAsOfDate());
        log.info(
                "DailyOhlcv listener [boll]: done asOfDate={} dailyRows={} bollRowsUpserted={}",
                event.getAsOfDate(), event.getRowsUpserted(), rows);
    }
}
