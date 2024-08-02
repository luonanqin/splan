package luonq.ibkr;

import bean.StockPosition;
import com.google.common.collect.Maps;
import com.ib.client.Contract;
import com.ib.client.Decimal;
import com.ib.client.Order;
import com.ib.client.OrderStatus;
import com.ib.client.OrderType;
import com.ib.client.Types;
import com.ib.controller.ApiController;

import java.util.Map;

public class TradeApi {

    public static long simulateUsOptionAccountId = 1;
    private OrderHandlerImpl orderHandler = new OrderHandlerImpl();
    private OrderCancelHandlerImpl orderCancelHandler = new OrderCancelHandlerImpl();
    private PositionHandlerImpl positionHandler = new PositionHandlerImpl();
    private int port;
    private ApiController client;

    public void useSimulateEnv() {
        port = 7497;
    }

    public void useRealEnv() {
        port = 7496;
    }

    public void setAccountId(long accountId) {
    }

    public void unlock() {
    }

    public void start() {
        ApiController.IConnectionHandler connectionHanlder = new ConnectionHanlderImpl();
        client = new ApiController(connectionHanlder);
        //        client.connect("127.0.0.1", 7496, 1, null); // 真实账户
        client.connect("127.0.0.1", port, 1, null); // 模拟账户

        orderHandler.setClient(client);
        orderCancelHandler.setClient(client);
        positionHandler.setClient(client);

    }

    public void end() {
        client.disconnect();
    }

    public long placeNormalBuyOrder(String code, double count, double price) {
        return placeNormalOrder(code, price, count, Types.SecType.OPT, Types.Action.BUY);
    }

    public long placeNormalBuyOrderForStock(String code, double count, double price) {
        return placeNormalOrder(code, price, count, Types.SecType.STK, Types.Action.BUY);
    }

    public long placeNormalSellOrder(String code, double count, double price) {
        return placeNormalOrder(code, price, count, Types.SecType.OPT, Types.Action.SELL);
    }

    public long placeNormalSellOrderForStock(String code, double count, double price) {
        return placeNormalOrder(code, price, count, Types.SecType.STK, Types.Action.SELL);
    }

    private Map<Integer/* orderId */, OrderHandlerImpl> orderIdToHandlerMap = Maps.newHashMap();
    private Map<Integer/* orderId */, Contract> orderIdToContractMap = Maps.newHashMap();
    private Map<Integer/* orderId */, Order> orderIdToOrderMap = Maps.newHashMap();

    public long placeNormalOrder(String code, double price, double count, Types.SecType secType, Types.Action action) {
        Contract contract = new Contract();
        contract.localSymbol(code);
        contract.secType(secType);
        contract.exchange("SMART");
        contract.currency("USD");

        Order order = new Order();
        order.action(action);
        order.lmtPrice(price);
        order.orderType(OrderType.LMT);
        order.totalQuantity(Decimal.get(count));

        OrderHandlerImpl orderHandler = new OrderHandlerImpl();
        client.placeOrModifyOrder(contract, order, orderHandler);
        int orderId = order.orderId();

        orderIdToContractMap.put(orderId, contract);
        orderIdToOrderMap.put(orderId, order);
        orderIdToHandlerMap.put(orderId, orderHandler);

        System.out.println("place order: action=" + action + " price=" + price + " count=" + count);
        return orderId;
    }

    // modify order
    public long upOrderPrice(long orderId, double count, double price) {
        modifyOrder((int) orderId, price, count);
        return orderId;
    }

    public void modifyOrder(int orderId, double price, double count) {
        if (!orderIdToOrderMap.containsKey(orderId) || !orderIdToContractMap.containsKey(orderId)) {
            // todo log
            return;
        }

        Contract contract = orderIdToContractMap.get(orderId);
        Order order = orderIdToOrderMap.get(orderId);
        order.lmtPrice(price);
        order.totalQuantity(Decimal.get(count));

        OrderHandlerImpl orderHandler = orderIdToHandlerMap.get(orderId);
        client.placeOrModifyOrder(contract, order, orderHandler);
        System.out.println("modify order: action=" + order.action() + " price=" + price + " count=" + count);
    }

    public long cancelOrder(long orderId) {
        OrderCancelHandlerImpl orderCancelHandler = new OrderCancelHandlerImpl();
        orderCancelHandler.setOrderId((int) orderId);
        client.cancelOrder((int) orderId, "", orderCancelHandler);

        return orderId;
    }

    public void removeOrderHandler(long orderId) {
        OrderHandlerImpl orderHandler = orderIdToHandlerMap.get((int) orderId);
        if (orderHandler == null) {
            return;
        }

        client.removeOrderHandler(orderHandler);
    }

    public bean.Order getOrder(long orderId) {
        OrderHandlerImpl orderHandler = orderIdToHandlerMap.get((int) orderId);
        if (orderHandler == null) {
            return null;
        }

        double avgPrice = orderHandler.getAvgPrice();
        OrderStatus status = orderHandler.getStatus();

        System.out.println("avgPrice: " + avgPrice + " status: " + status);

        bean.Order order = new bean.Order();
        order.setOrderID(orderId);
        order.setAvgPrice(avgPrice);
        order.setTradeCount(0);
        if (status == OrderStatus.Submitted) {
            order.setOrderStatus(5);
        } else if (status == OrderStatus.Filled) {
            order.setOrderStatus(11);
        } else if (status == OrderStatus.PreSubmitted) {
            order.setOrderStatus(1);
        } else if (status == OrderStatus.Cancelled) {
            order.setOrderStatus(15);
        }

        return order;
    }

    public Map<String, StockPosition> getPositionMap(String code) {
        client.reqPositions(positionHandler);
        return positionHandler.getPositionMap();
    }

    public void setPositionAvgCost(String code, double avgCost) {
        positionHandler.setAvgCost(code, avgCost);
    }

    public static void main(String[] args) {
        TradeApi tradeApi = new TradeApi();
        tradeApi.useSimulateEnv();
        tradeApi.start();

        int count = 100;
        tradeApi.positionHandler.setAvgCost("NVDA240802P00110000", count);
        String code = "NVDA  240802P00110000";
        long orderId = tradeApi.placeNormalBuyOrder(code, count, 0.83);
        //        long orderId = tradeApi.placeNormalBuyOrderForStock("AAPL", 1, 224.6);
        System.out.println("orderId: " + orderId);
        bean.Order order = tradeApi.getOrder(orderId);
        tradeApi.setPositionAvgCost(code, order.getAvgPrice());

        Map<String, StockPosition> positionMap = tradeApi.getPositionMap(null); // todo 要限制时间不能死等
        System.out.println("position: " + positionMap);

        //        long modifyOrderId = tradeApi.upOrderPrice(orderId, 4, 5.1);
        //        System.out.println("modifyOrderId: " + modifyOrderId);

        tradeApi.getOrder(orderId);

        //        tradeApi.removeOrderHandler(orderId);
        //        long sellorderId = tradeApi.placeNormalSellOrderForStock(code, count, 1.0);
        long sellorderId = tradeApi.placeNormalSellOrder(code, count, 0.9);

        tradeApi.cancelOrder(orderId);

        tradeApi.getOrder(orderId);

        System.out.println();

        //        tradeApi.end();
    }
}
