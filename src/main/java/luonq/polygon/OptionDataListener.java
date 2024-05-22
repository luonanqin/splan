package luonq.polygon;

import bean.Node;
import bean.NodeList;
import bean.StockEvent;
import bean.StockKLine;
import bean.StockOptionEvent;
import com.google.common.collect.Lists;
import com.google.common.eventbus.Subscribe;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

import java.util.List;
import java.util.Map;

@Data
@Slf4j
public class OptionDataListener {

    private NodeList nodeList;
    private List<StockOptionEvent> list = Lists.newLinkedList();
    private Map<String/* stock */, StockKLine> stockToKLineMap;

    @Subscribe
    public void onMessageEvent(StockEvent event) {
        cal(event);
    }

    public void cal(StockEvent event) {
        String stock = event.getStock();
        Double price = event.getPrice();

        if (!stockToKLineMap.containsKey(stock)) {
            log.info("there is no last kline for {}", stock);
            return;
        }

        double lastClose = stockToKLineMap.get(stock).getClose();
        if (price > lastClose) {
            return;
        }

        Double ratio = (lastClose - price) / lastClose;
        Node node = new Node(stock, ratio);
        node.setPrice(price);
        boolean success = nodeList.add(node);
        if (success) {
            nodeList.show();
        }
    }
}
