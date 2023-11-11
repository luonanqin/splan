package luonq.polygon;

import bean.Node;
import bean.NodeList;
import bean.RatioBean;
import bean.StockEvent;
import bean.StockRatio;
import com.google.common.collect.Sets;
import com.google.common.eventbus.Subscribe;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static java.math.BigDecimal.ROUND_DOWN;
import static java.math.BigDecimal.ROUND_HALF_UP;
import static luonq.polygon.RealTimeDataWS.originRatioMap;

/**
 * Created by Luonanqin on 2023/5/9.
 */
@Data
public class TradeDataListener {

    private NodeList list;
    private RealTimeDataWS client;

    @Subscribe
    public void onMessageEvent(StockEvent event) {
        cal(event);
    }

    public void cal(StockEvent event) {
        String stock = event.getStock();
        Double price = event.getPrice();

        Double m19closeSum = RealTimeDataWS.stockToM19CloseSum.get(stock);
        List<Double> m19closeList = RealTimeDataWS.stockToM19Close.get(stock);
        if (m19closeSum == null || CollectionUtils.isEmpty(m19closeList)) {
            return;
        }

        BigDecimal m20close = BigDecimal.valueOf(m19closeSum + price);
        double mb = m20close.divide(BigDecimal.valueOf(20), 2, ROUND_HALF_UP).doubleValue();
        BigDecimal avgDiffSum = BigDecimal.ZERO;

        m19closeList.add(price);
        for (double close : m19closeList) {
            avgDiffSum = avgDiffSum.add(BigDecimal.valueOf(close - mb).pow(2));
        }

        double md = Math.sqrt(avgDiffSum.doubleValue() / 20);
        BigDecimal mdPow2 = BigDecimal.valueOf(md).multiply(BigDecimal.valueOf(2));
        double dn = BigDecimal.valueOf(mb).subtract(mdPow2).setScale(3, ROUND_DOWN).doubleValue();

        if (dn < price) {
            return;
        }

        System.out.println(event + " dn=" + dn + " current=" + System.currentTimeMillis());
        double diff = (dn - price) / dn * 100;
        int diffInt = (int) diff;
        if (diffInt > 6) {
            diffInt = 6;
        }
        StockRatio stockRatio = originRatioMap.get(stock);
        Map<Integer, RatioBean> ratioMap = stockRatio.getRatioMap();
        RatioBean ratioBean = ratioMap.get(diffInt);
        if (ratioBean == null || ratioBean.getRatio() < RealTimeDataWS.HIT) {
            return;
        }

        Node node = new Node(stock, diff);
        node.setPrice(price);
        boolean success = list.add(node);
        if (success) {
            list.show();
            RealTimeDataWS.realtimeQuoteMap.put(stock, price);
        }
    }

    public static void main(String[] args) throws Exception {
        RealTimeDataWS.stockSet = Sets.newHashSet("RRGB");

        RealTimeDataWS.loadLatestMA20();
        TradeDataListener tradeDataListener = new TradeDataListener();
        tradeDataListener.cal(new StockEvent("RRGB", 10.03d, 1692027257000L));
    }
}
