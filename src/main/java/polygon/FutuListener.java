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
public class FutuListener {

    private NodeList list;
    private TradeApi tradeApi;
    private int cut = 995000;
    private List<String> tradeStock = Lists.newArrayList();
    private WebsocketClientEndpoint client;

    public FutuListener() {
        FTAPI.init();
        tradeApi = new TradeApi();
        tradeApi.useSimulateEnv();
        tradeApi.setAccountId(TradeApi.simulateUsAccountId);
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
            System.out.println("orderId: " + orderId);

            /** 4.下单完成后，十秒后获取成交状态 */
            OrderFill orderFill = tradeApi.getOrderFill(orderId, 10);
            if (orderFill == null) {
                /** 5.如果没有成交完成，则撤销剩下订单，并继续 */
                int cancelResCode = tradeApi.cancelOrder(orderId);
                // todo 打印撤单结果
                System.out.println("orderId: " + orderId + ", cancel res code: " + cancelResCode + "");
            } else {
                /** 5.1.如果成交完成，则继续 */
                // todo 打印成交结果
                System.out.println(orderFill);
                tradeStock.add(code);
            }

            /** 重新获取剩余可用现金 */
            remainCash = tradeApi.getFunds();
            remainCash -= cut;
        }
        System.out.println("trade end");

        /** 6.计算之前已成交的止损价格，并设置止损市价单（模拟盘不支持） */
        /** 6*.（只用于模拟盘，实盘需注释掉）计算止损价格临时存储，然后让主进程监听这些股票，发现低于止损价则触发止损限价单 */
        beginListenStopLoss();

        /** 建立timer，收盘前检查是否还有持仓，如果有，则取现价下单全部卖出 */
        closeCheckPosition();
    }

    public void closeCheckPosition() {
        Timer closeCheckTimer = new Timer();
        closeCheckTimer.schedule(new TimerTask() {
                                     @Override
                                     public void run() {
                                         String[] tradeStockArr = tradeStock.toArray(new String[tradeStock.size()]);
                                         Map<String, StockPosition> positionMap = tradeApi.getPositionMap(tradeStockArr);
                                         if (MapUtils.isEmpty(positionMap)) {
                                             System.out.println("持仓为空，今天的交易结束");
                                         } else {
                                             for (String stock : tradeStock) {
                                                 StockPosition stockPosition = positionMap.get(stock);
                                                 if (stockPosition == null) {
                                                     System.out.println(stock + " 持仓不存在");
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
                System.out.println(stock + " 持仓不存在");
                continue;
            }

            double canSellQty = stockPosition.getCanSellQty();
            double costPrice = stockPosition.getCostPrice();
            double lossPrice = BigDecimal.valueOf(costPrice * (1 - WebsocketClientEndpoint.LOSS_RATIO)).setScale(3, BigDecimal.ROUND_DOWN).doubleValue();
            StopLoss stopLoss = new StopLoss();
            stopLoss.setStock(stock);
            stopLoss.setCanSellQty(canSellQty);
            stopLoss.setLossPrice(lossPrice);

            stockToStopLoss.put(stock, stopLoss);
        }

        if (MapUtils.isEmpty(stockToStopLoss)) {
            System.out.println("没有持仓需要监听");
            return;
        }
        client.listenStopLoss(stockToStopLoss);
    }

    // 模拟盘不支持止损市价单
    public void placeStopLossOrder(String code) {
        Map<String, StockPosition> positionMap = tradeApi.getPositionMap(code);
        StockPosition stockPosition = positionMap.get(code);
        if (stockPosition == null) {
            System.out.println(code + " 持仓不存在");
            return;
        }

        double canSellQty = stockPosition.getCanSellQty();
        double costPrice = stockPosition.getCostPrice();
        double auxPrice = BigDecimal.valueOf(costPrice * (1 - WebsocketClientEndpoint.LOSS_RATIO)).setScale(3, BigDecimal.ROUND_DOWN).doubleValue();

        long orderId = tradeApi.placeOrderForLossMarket(code, canSellQty, auxPrice);
        System.out.println(code + " 止损单下单成功 订单号：" + orderId);
    }

    public void placeStopLossOrder(String code, double canSellQty, double price) {
        long orderId = tradeApi.placeOrderForLossNormal(code, canSellQty, price);
        System.out.println(code + " 普通卖出限价单下单成功 订单号：" + orderId);
    }

    public static void main(String[] args) {
        FutuListener futuListener = new FutuListener();
        NodeList nodeList = new NodeList(10);
        Node node = new Node("RNAZ", 1);
        node.setPrice(5.71d);
        nodeList.add(node);
        futuListener.setList(nodeList);

        //        futuListener.beginTrade();
        futuListener.placeStopLossOrder("RNAZ");
    }
}
