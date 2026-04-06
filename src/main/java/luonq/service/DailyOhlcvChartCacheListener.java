package luonq.service;

import lombok.extern.slf4j.Slf4j;
import luonq.event.DailyOhlcvUpsertedEvent;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.event.EventListener;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

/**
 * 日 K 写入后：使图表查询缓存失效（先于 MA/BOLL/周月季 K）。
 */
@Component
@Slf4j
public class DailyOhlcvChartCacheListener {

    private final ChartDataChangeNotifier chartDataChangeNotifier;

    public DailyOhlcvChartCacheListener(ChartDataChangeNotifier chartDataChangeNotifier) {
        this.chartDataChangeNotifier = chartDataChangeNotifier;
    }

    @Order(10)
    @EventListener
    public void onDailyOhlcvUpserted(DailyOhlcvUpsertedEvent event) {
        if (event.getRowsUpserted() <= 0) {
            return;
        }
        if (StringUtils.isNotBlank(event.getSingleSymbolOrNull())) {
            String sym = event.getSingleSymbolOrNull().trim();
            chartDataChangeNotifier.notifySymbolChanged(sym);
            log.info(
                    "DailyOhlcv listener [chart-cache]: done asOfDate={} dailyRows={} action=notifySymbol symbol={}",
                    event.getAsOfDate(), event.getRowsUpserted(), sym);
        } else {
            chartDataChangeNotifier.notifyAllSymbolsChanged();
            log.info(
                    "DailyOhlcv listener [chart-cache]: done asOfDate={} dailyRows={} action=notifyAllSymbols",
                    event.getAsOfDate(), event.getRowsUpserted());
        }
    }
}
