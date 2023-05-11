package polygon;

import com.google.common.eventbus.Subscribe;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;

import java.math.BigDecimal;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;

import static java.math.BigDecimal.ROUND_DOWN;

/**
 * Created by Luonanqin on 2023/5/9.
 */
public class TradeListener {


    @Data
    static class BollDiff {
        String stock;
        double diff;

        BollDiff(String stock, double diff){
            this.stock = stock;
            this.diff = diff;
        }
    }

    private PriorityQueue<BollDiff> queue = new PriorityQueue<>(100, (o1, o2) -> {
        if (o1.getDiff() > o2.getDiff()) {
            return -1;
        } else {
            return 1;
        }
    });

    @Subscribe
    public void onMessageEvent(Map.Entry<String, Double> entry) {
        String stock = entry.getKey();
        Double price = entry.getValue();
        cal(stock, price);
    }

    public void cal(String stock, double price) {
        Double m19closeSum = WebsocketClientEndpoint.stockToM19CloseSum.get(stock);
        Set<Double> m19closeSet = WebsocketClientEndpoint.stockToM19Close.get(stock);
        if (m19closeSum == null || CollectionUtils.isEmpty(m19closeSet)) {
            return;
        }

        double mb = (m19closeSum + price) / 20;
        BigDecimal avgDiffSum = BigDecimal.ZERO;

        m19closeSet.add(price);
        for (double close : m19closeSet) {
            avgDiffSum = avgDiffSum.add(BigDecimal.valueOf(close - mb).pow(2));
        }

        double md = Math.sqrt(avgDiffSum.doubleValue() / 20);
        BigDecimal mdPow2 = BigDecimal.valueOf(md).multiply(BigDecimal.valueOf(2));
        double dn = BigDecimal.valueOf(mb).subtract(mdPow2).setScale(3, ROUND_DOWN).doubleValue();

        System.out.println(stock + " price=" + price + " dn=" + dn);
        double diff = (price - dn) / dn;
        BollDiff bollDiff = new BollDiff(stock, diff);
        queue.offer(bollDiff);
    }
}
