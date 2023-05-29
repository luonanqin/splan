package polygon;

import bean.Node;
import bean.NodeList;
import com.google.common.eventbus.Subscribe;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;
import strategy.OverBollingerDN2023OpenFirst;

import java.math.BigDecimal;
import java.util.Map;
import java.util.Set;

import static java.math.BigDecimal.ROUND_DOWN;
import static polygon.RealTimeDataWS.originRatioMap;

/**
 * Created by Luonanqin on 2023/5/9.
 */
@Data
public class TradeListener {

    private NodeList list;
    private RealTimeDataWS client;

    @Subscribe
    public void onMessageEvent(Map.Entry<String, Double> entry) {
        String stock = entry.getKey();
        Double price = entry.getValue();
        cal(stock, price);
    }

    public void cal(String stock, double price) {
        Double m19closeSum = RealTimeDataWS.stockToM19CloseSum.get(stock);
        Set<Double> m19closeSet = RealTimeDataWS.stockToM19Close.get(stock);
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

        if (dn < price) {
            return;
        }

        System.out.println(stock + " price=" + price + " dn=" + dn);
        double diff = (dn - price) / dn;
        int diffInt = (int) diff;
        OverBollingerDN2023OpenFirst.StockRatio stockRatio = originRatioMap.get(stock);
        Map<Integer, OverBollingerDN2023OpenFirst.RatioBean> ratioMap = stockRatio.getRatioMap();
        OverBollingerDN2023OpenFirst.RatioBean ratioBean = ratioMap.get(diffInt);
        if (ratioBean == null || ratioBean.getRatio() < 0.5) {
            return;
        }

        Node node = new Node(stock, diff);
        node.setPrice(price);
        boolean success = list.add(node);
        if (success) {
            list.show();
        }
    }
}
