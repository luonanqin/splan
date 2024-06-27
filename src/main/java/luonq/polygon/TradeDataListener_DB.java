package luonq.polygon;

import bean.Node;
import bean.NodeList;
import bean.RatioBean;
import bean.StockEvent;
import bean.StockRatio;
import com.google.common.collect.Sets;
import com.google.common.eventbus.Subscribe;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static java.math.BigDecimal.ROUND_DOWN;
import static java.math.BigDecimal.ROUND_HALF_UP;
import static luonq.polygon.RealTimeDataWS_DB.originRatioMap;

/**
 * Created by Luonanqin on 2023/5/9.
 */
@Data
@Slf4j
public class TradeDataListener_DB {

    private NodeList list;
    private RealTimeDataWS_DB client;
    private Set<String> stockSet;

    @Subscribe
    public void onMessageEvent(StockEvent event) {
        cal(event);
    }

    public void cal(StockEvent event) {
        String stock = event.getStock();
        if (!stockSet.contains(stock)) {
            return;
        }

        Double price = event.getPrice();
        if (price < RealTimeDataWS_DB.PRICE_LIMIT) {
            return;
        }

        Double lastDn = RealTimeDataWS_DB.stockToLastDn.get(stock);
        if (lastDn == null || price > lastDn) {
            return;
        }

        Double m19closeSum = RealTimeDataWS_DB.stockToM19CloseSum.get(stock);
        List<Double> m19closeList = RealTimeDataWS_DB.stockToM19Close.get(stock);
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

        log.info(event + " dn=" + dn + " current=" + System.currentTimeMillis());
        if (dn < price) {
            return;
        }

        double diff = (dn - price) / dn * 100;
        int diffInt = (int) diff;
        if (diffInt > 6) {
            diffInt = 6;
        }
        StockRatio stockRatio = originRatioMap.get(stock);
        Map<Integer, RatioBean> ratioMap = stockRatio.getRatioMap();
        RatioBean ratioBean = ratioMap.get(diffInt);
        if (ratioBean == null || ratioBean.getRatio() < RealTimeDataWS_DB.HIT) {
            return;
        }

        Node node = new Node(stock, diff);
        node.setPrice(price);
        boolean success = list.add(node);
        if (success) {
            log.info("Node list show: {}", list.show());
            RealTimeDataWS_DB.realtimeQuoteMap.put(stock, price);
        }
    }

    public static void main(String[] args) throws Exception {
        RealTimeDataWS_DB.stockSet = Sets.newHashSet("RRGB");

//        RealTimeDataWS_DB.loadLatestMA20();
        TradeDataListener_DB tradeDataListener = new TradeDataListener_DB();
        tradeDataListener.cal(new StockEvent("RRGB", 10.03d, 1692027257000L));
    }
}
