package luonq.service;

import luonq.event.ChartSourceDataChangedEvent;
import org.apache.commons.lang3.StringUtils;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * 在 K 线或指标数据写入后通知图表缓存失效，避免长期运行进程一直命中旧缓存。
 */
@Service
public class ChartDataChangeNotifier {

    private final ApplicationEventPublisher publisher;

    public ChartDataChangeNotifier(ApplicationEventPublisher publisher) {
        this.publisher = publisher;
    }

    /** 仅失效该代码对应的缓存条目（与请求里 symbol 大小写无关，由消费者按忽略大小写匹配）。 */
    public void notifySymbolChanged(String symbol) {
        if (StringUtils.isBlank(symbol)) {
            return;
        }
        publisher.publishEvent(new ChartSourceDataChangedEvent(symbol.trim()));
    }

    /** 全量同步或影响面无法逐票列举时，清空全部图表缓存。 */
    public void notifyAllSymbolsChanged() {
        publisher.publishEvent(new ChartSourceDataChangedEvent(null));
    }
}
