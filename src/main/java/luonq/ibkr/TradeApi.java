package luonq.ibkr;

import bean.StockPosition;
import com.ib.client.Order;
import com.ib.client.OrderStatus;
import com.ib.client.Types;
import com.ib.controller.ApiController;

import java.util.List;
import java.util.Map;

public class TradeApi {

    private OrderHandlerImpl orderHandler = new OrderHandlerImpl();
    private OrderCancelHandlerImpl orderCancelHandler = new OrderCancelHandlerImpl();
    private PositionHandlerImpl positionHandler = new PositionHandlerImpl();
    private int port;
    public static long simulateUsOptionAccountId = 1;

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
        ApiController client = new ApiController(connectionHanlder);
        //        client.connect("127.0.0.1", 7496, 1, null); // 真实账户
        client.connect("127.0.0.1", port, 1, null); // 模拟账户

        orderHandler.setClient(client);
        orderCancelHandler.setClient(client);
        positionHandler.setClient(client);
    }

    public long placeNormalBuyOrder(String code, double count, double price) {
        return orderHandler.placeNormalOrder(code, price, count, Types.Action.BUY);
    }

    public long placeNormalSellOrder(String code, double count, double price) {
        return orderHandler.placeNormalOrder(code, price, count, Types.Action.SELL);
    }

    // modify order
    public long upOrderPrice(long orderId, double count, double price) {
        orderHandler.modifyOrder((int) orderId, price, count);
        return orderId;
    }

    public long cancelOrder(long orderId) {
        orderCancelHandler.cancelOrder((int) orderId);
        return orderId;
    }

    public bean.Order getOrder(long orderId) {
        Order order = orderHandler.getOrder((int) orderId);
        if (order == null) {
            return null;
        }

        OrderStatus orderStatus = orderHandler.getOrderStatus((int) orderId);

        return null;
    }

    public void getOrderList(List<Long> orderIds) {
    }


    public Map<String, StockPosition> getPositionMap(String code) {
        Map<String, StockPosition> positionMap = positionHandler.getPositionMap();
        return positionMap;
    }
}
