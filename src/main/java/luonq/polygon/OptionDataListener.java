package luonq.polygon;

import bean.Node;
import bean.NodeList;
import bean.StockOptionEvent;
import com.google.common.collect.Lists;
import com.google.common.eventbus.Subscribe;
import lombok.Data;

import java.util.List;

@Data
public class OptionDataListener {

    private NodeList nodeList = new NodeList(10);
    private List<StockOptionEvent> list = Lists.newLinkedList();

    @Subscribe
    public void onMessageEvent(StockOptionEvent event) {
        cal(event);
    }

    public void cal(StockOptionEvent event) {
        if (event.getLastClose() < event.getPrice()) {
            return;
        }
        String stock = event.getStock();
        Double ratio = event.getRatio();
        Double price = event.getPrice();
        Node node = new Node(stock, ratio);
        node.setPrice(price);
        boolean success = nodeList.add(node);
        if (success) {
            nodeList.show();
        }
    }
}
