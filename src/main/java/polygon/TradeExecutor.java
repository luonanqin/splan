package polygon;

import bean.Node;
import bean.NodeList;
import bean.OrderFill;
import bean.StockPosition;
import bean.StopLoss;
import com.futu.openapi.FTAPI;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import futu.TradeApi;
import lombok.Data;
import org.apache.commons.collections4.MapUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

/**
 * Created by Luonanqin on 2023/5/9.
 */
@Data
public class TradeExecutor {

    private NodeList list;
    private TradeApi tradeApi;
    private int cut = 990000;
    private List<String> tradeStock = Lists.newArrayList();
    private RealTimeDataWS client;

    public TradeExecutor() {
        FTAPI.init();
        tradeApi = new TradeApi();
        //        tradeApi.useSimulateEnv();
        //        tradeApi.setAccountId(TradeApi.simulateUsAccountId);
        tradeApi.start();
        tradeApi.unlock();
    }

    public void beginTrade() {
        List<Node> nodes = list.getNodes();
        /** 1.获取剩余可用现金 */
        double remainCash = tradeApi.getFunds();
        System.out.println("remain cash: " + remainCash);
        remainCash -= cut;
        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);
            String code = node.getName();
            double price = node.getPrice();
            /** 2.用剩余可用现金计算可买数量 */
            int count = (int) (remainCash / price);

            /** 如果可买数量为0，则执行56 */
            if (count <= 0) {
                System.out.println("trade finish");
                break;
            }

            /** 3.用可买数量下市价单 */
            long orderId = tradeApi.placeOrder(code, count, price);
            System.out.println("buy stock. stock=" + code + ", count=" + count + ", price=" + price + ",orderId: " + orderId);

            /** 4.下单完成后，十秒后获取成交状态 */
            OrderFill orderFill = tradeApi.getOrderFill(orderId, 10);
            if (orderFill == null) {
                /** 5.如果没有成交完成，则撤销剩下订单，并继续 */
                int cancelResCode = tradeApi.cancelOrder(orderId);
                System.out.println(code + " order has been canceled. orderId: " + orderId + ", cancel res code: " + cancelResCode);
            } else {
                /** 5.1.如果成交完成，则马上设置止损单，但只针对实盘 */
                System.out.println(orderFill);
                //                placeStopLossOrder(code);
                tradeStock.add(code);
            }

            /** 重新获取剩余可用现金 */
            remainCash = tradeApi.getFunds();
            remainCash -= cut;
        }
        System.out.println("trade end");

        /** 6.建立timer，收盘前检查是否还有持仓，如果有，则取现价下单全部卖出 */
        closeCheckPosition();

        /** 7.计算之前已成交的止损价格，并设置止损市价单（模拟盘不支持） */
        /** 7*.（只用于模拟盘，实盘需注释掉）计算止损价格临时存储，然后让主进程监听这些股票，发现低于止损价则触发止损限价单 */
        beginListenStopLoss();
    }

    public void closeCheckPosition() {
        Timer closeCheckTimer = new Timer();
        closeCheckTimer.schedule(new TimerTask() {
                                     @Override
                                     public void run() {
                                         String[] tradeStockArr = tradeStock.toArray(new String[tradeStock.size()]);
                                         Map<String, StockPosition> positionMap = tradeApi.getPositionMap(tradeStockArr);
                                         if (MapUtils.isEmpty(positionMap)) {
                                             System.out.println("position is empty. trade is end today!!!");
                                         } else {
                                             for (String stock : tradeStock) {
                                                 StockPosition stockPosition = positionMap.get(stock);
                                                 if (stockPosition == null) {
                                                     System.out.println("closeCheckPosition failed! " + stock + " position is not exist!");
                                                     continue;
                                                 }

                                                 double canSellQty = stockPosition.getCanSellQty();
                                                 double currPrice = stockPosition.getCurrPrice();
                                                 long orderId = tradeApi.placeOrderForLossNormal(stock, canSellQty, currPrice - 0.5d);
                                                 System.out.println(stock + " 收盘前卖出限价单下单成功 订单号：" + orderId);
                                             }
                                         }
                                     }
                                 }
          , client.getCloseCheckTime());
    }

    public void beginListenStopLoss() {
        Map<String, StopLoss> stockToStopLoss = Maps.newHashMap();
        String[] tradeStockArr = tradeStock.toArray(new String[tradeStock.size()]);
        Map<String, StockPosition> positionMap = tradeApi.getPositionMap(tradeStockArr);
        for (String stock : tradeStock) {
            StockPosition stockPosition = positionMap.get(stock);
            if (stockPosition == null) {
                System.out.println("beginListenStopLoss failed! " + stock + " position is not exist!");
                continue;
            }

            double canSellQty = stockPosition.getCanSellQty();
            double costPrice = stockPosition.getCostPrice();
            double lossPrice = BigDecimal.valueOf(costPrice * (1 - RealTimeDataWS.LOSS_RATIO)).setScale(3, BigDecimal.ROUND_DOWN).doubleValue();
            StopLoss stopLoss = new StopLoss();
            stopLoss.setStock(stock);
            stopLoss.setCanSellQty(canSellQty);
            stopLoss.setLossPrice(lossPrice);

            stockToStopLoss.put(stock, stopLoss);
        }

        if (MapUtils.isEmpty(stockToStopLoss)) {
            System.out.println("there is no position that need to be listened");
            return;
        }
        client.listenStopLoss(stockToStopLoss);
    }

    // 模拟盘不支持止损市价单
    public void placeStopLossOrder(String code) {
        Map<String, StockPosition> positionMap = tradeApi.getPositionMap(code);
        StockPosition stockPosition = positionMap.get(code);
        if (stockPosition == null) {
            System.out.println("placeStopLossOrder faild! " + code + " position is not exist!");
            return;
        }

        double canSellQty = stockPosition.getCanSellQty();
        double costPrice = stockPosition.getCostPrice();
        double auxPrice = BigDecimal.valueOf(costPrice * (1 - RealTimeDataWS.LOSS_RATIO)).setScale(3, BigDecimal.ROUND_DOWN).doubleValue();

        long orderId = tradeApi.placeOrderForLossMarket(code, canSellQty, auxPrice);
        System.out.println(code + " placeStopLoss market order. qty=" + canSellQty + ", auxPrice=" + auxPrice + ", orderId：" + orderId);
    }

    public void placeStopLossOrder(String code, double canSellQty, double price) {
        long orderId = tradeApi.placeOrderForLossNormal(code, canSellQty, price);
        System.out.println(code + " placeStopLoss limit order. qty=" + canSellQty + ", price=" + price + ", orderId: " + orderId);
    }

    public StockPosition getPosition(String stock) {
        Map<String, StockPosition> positionMap = tradeApi.getPositionMap(stock);
        return positionMap.get(stock);
    }
    public static void main(String[] args) {
        TradeExecutor tradeExecutor = new TradeExecutor();
        //        NodeList nodeList = new NodeList(10);
        //        Node node = new Node("RNAZ", 1);
        //        node.setPrice(5.71d);
        //        nodeList.add(node);
        //        tradeExecutor.setList(nodeList);

        //        futuListener.beginTrade();
        tradeExecutor.placeStopLossOrder("");
    }
}
