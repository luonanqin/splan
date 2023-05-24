package polygon;

import bean.Node;
import bean.NodeList;
import bean.OrderFill;
import bean.StockPosition;
import com.futu.openapi.FTAPI;
import futu.TradeApi;
import lombok.Data;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Created by Luonanqin on 2023/5/9.
 */
@Data
public class FutuListener {

    private NodeList list;
    private TradeApi tradeApi;
    private int cut = 995000;

    public FutuListener() {
        FTAPI.init();
        tradeApi = new TradeApi();
        tradeApi.useSimulateEnv();
        tradeApi.setAccountId(TradeApi.simulateUsAccountId);
        tradeApi.start();
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
                /** 6.计算之前已成交的止损价格，并设置止损市价单 */
                placeStopLossOrder(code);
            }

            /** 重新获取剩余可用现金 */
            remainCash = tradeApi.getFunds();
            remainCash -= cut;
        }
        System.out.println("trade end");
    }

    public void placeStopLossOrder(String code) {
        new Thread(() -> {
            Map<String, StockPosition> positionMap = tradeApi.getPositionMap(code);
            StockPosition stockPosition = positionMap.get(code);
            if (stockPosition == null) {
                System.out.println(code + " 持仓不存在");
                return;
            }

            double canSellQty = stockPosition.getCanSellQty();
            double costPrice = stockPosition.getCostPrice();
            double auxPrice = BigDecimal.valueOf(costPrice * (1 - WebsocketClientEndpoint.LOSS_RATIO)).setScale(3).doubleValue();

            long orderId = tradeApi.placeOrderForLossMarket(code, canSellQty, auxPrice);
            System.out.println(code + " 止损下单成功 订单号：" + orderId);
        }).start();
    }

    public static void main(String[] args) {
        FutuListener futuListener = new FutuListener();
        NodeList nodeList = new NodeList(10);
        Node node = new Node("RNAZ", 1);
        node.setPrice(5.77d);
        nodeList.add(node);
        futuListener.setList(nodeList);

        futuListener.beginTrade();
    }
}
