package luonq.polygon;

import bean.Node;
import bean.NodeList;
import bean.OrderFill;
import bean.StockPosition;
import bean.StopLoss;
import com.futu.openapi.FTAPI;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.Data;
import luonq.futu.TradeApi;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

import static util.Constants.TRADE_ERROR_CODE;

/**
 * Created by Luonanqin on 2023/5/9.
 */
//@Component
@Data
public class TradeExecutor {

    private NodeList list;
    private TradeApi tradeApi;
    //    private TradeApi tradeApi2;
    private int cut = 990000;
    private List<String> tradeStock = Lists.newArrayList();
    private RealTimeDataWS client;
    private boolean realTrade = true;

    public TradeExecutor() {
        FTAPI.init();
        tradeApi = new TradeApi();
        //        tradeApi.useSimulateEnv();
        //        tradeApi.setAccountId(TradeApi.simulateUsAccountId);
        tradeApi.useRealEnv();
        tradeApi.start();
        tradeApi.unlock();

        //        tradeApi2 = new TradeApi();
        //        tradeApi2.useRealEnv();
        //        tradeApi2.start();
        //        tradeApi2.unlock();
    }

    public void setTradeStock(List<String> stocks) {
        tradeStock = stocks;
    }

    public void beginTrade() throws InterruptedException {
        list.show();
        List<Node> nodes = list.getNodes();
        /** 1.获取剩余可用现金 */
        double remainCash = tradeApi.getFunds();
        if (!realTrade) {
            remainCash -= cut;
        }
        System.out.println("remain cash: " + remainCash);
        if (CollectionUtils.isNotEmpty(nodes)) {
            Node node = nodes.get(0);
            String code = node.getName();
            double price;
            if (!RealTimeDataWS.realtimeQuoteMap.containsKey(code)) {
                System.out.println("can't find real-time quote: " + code);
                price = node.getPrice();
            } else {
                price = RealTimeDataWS.realtimeQuoteMap.get(code);
            }

            double upRatio = 0.003;
            int multiple = 0;
            double orderPrice = BigDecimal.valueOf(price * (1 + upRatio * (++multiple))).setScale(2, BigDecimal.ROUND_FLOOR).doubleValue();
            System.out.println("first trade. orderPrice=" + orderPrice + ", multiple=" + multiple);
            int count = tradeApi.getMaxCashBuy(code, orderPrice);

            while (true) {
                //            /** 2.用剩余可用现金计算可买数量 */
                //            int count = (int) (remainCash / orderPrice);
                /** 2.调用接口获取最大可买数量 */
                if (count == TRADE_ERROR_CODE) {
                    System.out.println("get max cash buy error. code=" + code + ", orderPrice=" + orderPrice);
                    break;
                }
                /** 如果可买数量为0，则执行56 */
                if (count <= 0) {
                    System.out.println("there is no cash to trade. trade finish. count=" + count);
                    break;
                }

                /** 3.用可买数量下限价单 */
                long orderId;
                if (realTrade) {
                    //                orderId = tradeApi.placeMarketBuyOrder(code, count);
                    orderId = tradeApi.placeNormalBuyOrder(code, count, orderPrice);
                } else {
                    orderId = tradeApi.placeNormalBuyOrder(code, count, orderPrice);
                }
                if (orderId == TRADE_ERROR_CODE) {
                    System.out.println("place order failed. code=" + code + ", count=" + count + ", orderPrice=" + orderPrice);
                    count--;
                    continue;
                }
                System.out.println("buy stock. stock=" + code + ", count=" + count + ", price=" + price + ", orderPrice=" + orderPrice + ", orderId: " + orderId + " " + System.currentTimeMillis());

                while (true) {
                    /** 4.下单完成后，十秒后获取成交状态 */
                    OrderFill orderFill = tradeApi.getOrderFill(orderId, 4);
                    if (orderFill == null) {
                        /** 5.如果没有成交完成，则修改价格，并继续 */
                        orderPrice = BigDecimal.valueOf(price * (1 + upRatio * (++multiple))).setScale(2, BigDecimal.ROUND_FLOOR).doubleValue();
                        System.out.println("modify order price. orderPrice=" + orderPrice + ", multiple=" + multiple);

                        while (true) {
                            long modifyOrderId = tradeApi.upOrderPrice(orderId, count, orderPrice);
                            System.out.println(code + " order has been modify. orderId=" + orderId + ", ordePrice=" + orderPrice + ", modifyOrderId=" + modifyOrderId);
                            //                    System.out.println(code + " order has been canceled. orderId: " + orderId + ", cancel res code: " + modifyOrderId);
                            if (modifyOrderId == TRADE_ERROR_CODE) {
                                count--;
                                System.out.println("minus count=" + count);
                            } else {
                                break;
                            }
                        }
                    } else {
                        /** 5.1.如果成交完成，则马上设置止损单，但只针对实盘 */
                        System.out.println(code + " order is successfully executed. orderId=" + orderId);
                        if (realTrade) {
                            try {
                                placeStopLossOrder(orderFill);
                            } catch (Exception e) {
                                System.out.println("real placeStopLossOrder failed");
                            }
                        }
                        tradeStock.add(code);
                        break;
                    }
                }

                if (!realTrade) {
                    /** 模拟盘重新获取剩余可用现金 */
                    remainCash = tradeApi.getFunds();
                    remainCash -= cut;
                    //            } else if (orderFill != null) {
                    //                /** 实盘获取的剩余现金实时性不高，所以用前值减去已成交订单金额得到最新现金 */
                    //                remainCash = remainCash - orderFill.getAvgPrice() * orderFill.getCount();
                }
                break;
            }
        }
        System.out.println("trade end");

        // 停止监听实时报价
        RealTimeDataWS.getRealtimeQuote = false;
        System.out.println("stop get real-time quote");

        /** 6.建立timer，收盘前检查是否还有持仓，如果有，则取现价下单全部卖出 */
        //        closeCheckPosition();

        Thread.sleep(10000);
        /** 7.计算之前已成交的止损价格，并设置止损市价单（模拟盘不支持） */
        /** 7*.（只用于模拟盘，实盘需注释掉）计算止损价格临时存储，然后让主进程监听这些股票，发现低于止损价则触发止损限价单 */
        if (!realTrade) {
            beginListenStopLoss();
        }
    }

    public void closeCheckPosition() {
        Timer closeCheckTimer = new Timer();
        closeCheckTimer.schedule(new TimerTask() {
                                     @Override
                                     public void run() {
                                         String[] tradeStockArr = tradeStock.toArray(new String[tradeStock.size()]);
                                         Map<String, StockPosition> positionMap = tradeApi.getPositionMap(tradeStockArr);
                                         System.out.println("ready to sell before close market. position=" + positionMap + ", time=" + LocalDateTime.now());
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
                                                 double orderPrice = BigDecimal.valueOf(currPrice * 0.995).setScale(2, BigDecimal.ROUND_FLOOR).doubleValue();
                                                 System.out.println("before close sell stock. stock=" + stock + ", count=" + canSellQty + ", currentPrice=" + currPrice + ", orderPrice=" + orderPrice);
                                                 long orderId = tradeApi.placeNormalSellOrder(stock, canSellQty, orderPrice);
                                                 System.out.println("before close sell stock. stock=" + stock + ", orderId：" + orderId);
                                             }
                                         }
                                     }
                                 }
          , client.getCloseCheckTime());
        System.out.println("begin close check timer listen!");
    }

    public void closeSell() {
        System.out.println("sell stock before close market");
        Map<String, StockPosition> positionMap = tradeApi.getPositionMap(null);
        System.out.println("ready to sell before close market. position=" + positionMap + ", time=" + LocalDateTime.now());
        if (MapUtils.isEmpty(positionMap)) {
            System.out.println("position is empty. trade is end today!!!");
        } else {
            for (String stock : positionMap.keySet()) {
                StockPosition stockPosition = positionMap.get(stock);
                if (stockPosition == null) {
                    System.out.println("closeCheckPosition failed! " + stock + " position is not exist!");
                    continue;
                }

                /** 收盘前卖出尝试3次 */
                for (int i = 0; i < 3; i++) {
                    double canSellQty = stockPosition.getCanSellQty();

                    System.out.println("before close sell stock for market price. stock=" + stock + ", count=" + canSellQty);
                    long orderId = tradeApi.placeMarketSellOrder(stock, canSellQty);
                    System.out.println("sell stock orderId=" + orderId);
                    if (orderId == -1) {
                        System.out.println("retry sell stock=" + stock);
                        continue;
                    }
                    OrderFill orderFill = tradeApi.getOrderFill(orderId, 8);
                    if (orderFill == null) {
                        long cancelResCode = tradeApi.cancelOrder(orderId);
                        System.out.println("sell stock cancel. retry stock=" + stock + ", orderId=" + orderId + ", cancelResCode=" + cancelResCode);
                        continue;
                    }
                    System.out.println("sell stock success. stock=" + stock + ", orderId=" + orderId);
                    break;
                }
            }
        }
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
            //            System.exit(0);
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

    // 根据订单返回的信息设定止损单
    public void placeStopLossOrder(OrderFill orderFill) {
        if (orderFill == null) {
            System.out.println("order fill is null");
            return;
        }

        String code = orderFill.getCode();
        double canSellQty = orderFill.getCount();
        double costPrice = orderFill.getAvgPrice();
        double auxPrice = BigDecimal.valueOf(costPrice * (1 - RealTimeDataWS.LOSS_RATIO)).setScale(3, BigDecimal.ROUND_DOWN).doubleValue();

        long orderId = tradeApi.placeOrderForLossMarket(code, canSellQty, auxPrice);
        System.out.println(code + " placeStopLoss market order. qty=" + canSellQty + ", auxPrice=" + auxPrice + ", orderId：" + orderId);
    }

    public void placeStopLossOrder(String code, double canSellQty, double price) {
        long orderId = tradeApi.placeNormalSellOrder(code, canSellQty, price);
        System.out.println(code + " placeStopLoss limit order. qty=" + canSellQty + ", price=" + price + ", orderId: " + orderId);
    }

    public StockPosition getPosition(String stock) {
        Map<String, StockPosition> positionMap = tradeApi.getPositionMap(stock);
        if (positionMap == null) {
            System.out.println("get " + stock + "position is null");
            return null;
        }
        return positionMap.get(stock);
    }

    public Map<String, StockPosition> getAllPosition() {
        return tradeApi.getPositionMap(null);
    }

    public void reListenStopLoss() {
        Map<String, StopLoss> stockToStopLoss = Maps.newHashMap();
        Map<String, StockPosition> positionMap = tradeApi.getPositionMap(null);
        for (String stock : positionMap.keySet()) {
            StockPosition stockPosition = positionMap.get(stock);

            double canSellQty = stockPosition.getCanSellQty();
            double costPrice = stockPosition.getCostPrice();
            double lossPrice = BigDecimal.valueOf(costPrice * (1 - RealTimeDataWS.LOSS_RATIO)).setScale(3, BigDecimal.ROUND_DOWN).doubleValue();
            StopLoss stopLoss = new StopLoss();
            stopLoss.setStock(stock);
            stopLoss.setCanSellQty(canSellQty);
            stopLoss.setLossPrice(lossPrice);

            stockToStopLoss.put(stock, stopLoss);
        }

        client.listenStopLoss(stockToStopLoss);
    }

    public static void main(String[] args) {
        TradeExecutor tradeExecutor = new TradeExecutor();
        //        NodeList nodeList = new NodeList(10);
        //        Node node = new Node("RNAZ", 1);
        //        node.setPrice(5.71d);
        //        nodeList.add(node);
        //        tradeExecutor.setList(nodeList);

        //        futuListener.beginTrade();
        tradeExecutor.placeStopLossOrder("HKD");
    }
}
