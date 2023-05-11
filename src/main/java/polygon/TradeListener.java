package polygon;

import com.google.common.eventbus.Subscribe;
import org.apache.commons.collections4.CollectionUtils;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import static java.math.BigDecimal.ROUND_DOWN;

/**
 * Created by Luonanqin on 2023/5/9.
 */
public class TradeListener {

    @Subscribe
    public void onMessageEvent(Map.Entry<String, Double> entry) {
        String stock = entry.getKey();
        Double price = entry.getValue();
        cal(stock, price);
    }

    public void cal(String stock, double price) {
        Double m19closeSum = TradeWSClient.stockToM19CloseSum.get(stock);
        Set<Double> m19closeSet = TradeWSClient.stockToM19Close.get(stock);
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
    }
}
