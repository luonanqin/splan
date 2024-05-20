package luonq.polygon;

import bean.StockOptionEvent;
import com.google.common.collect.Lists;
import com.google.common.eventbus.Subscribe;
import lombok.Data;

import java.util.List;

@Data
public class OptionDataListener {

    private List<StockOptionEvent> list = Lists.newLinkedList();

    @Subscribe
    public void onMessageEvent(StockOptionEvent event) {
        cal(event);
    }

    public void cal(StockOptionEvent event) {
        if (event.getLastClose() < event.getPrice()) {
            return;
        }
        list.add(event);

        list.sort((o1, o2) -> o2.getRatio().compareTo(o1.getRatio()));
    }

    public static void main(String[] args) throws Exception {
        StockOptionEvent s1 = new StockOptionEvent();
        s1.setLastClose(1.5);
        s1.setPrice(3d);

        StockOptionEvent s2 = new StockOptionEvent();
        s2.setLastClose(100.4);
        s2.setPrice(95.2);

        StockOptionEvent s3 = new StockOptionEvent();
        s3.setLastClose(50.32);
        s3.setPrice(40.12);

        OptionDataListener optionDataListener = new OptionDataListener();
        optionDataListener.cal(s1);
        optionDataListener.cal(s2);
        optionDataListener.cal(s3);

        System.out.println(optionDataListener.getList());
    }
}
