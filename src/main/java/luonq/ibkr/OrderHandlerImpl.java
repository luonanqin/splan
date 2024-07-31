package luonq.ibkr;

import com.google.common.collect.Maps;
import com.ib.client.Contract;
import com.ib.client.Decimal;
import com.ib.client.Order;
import com.ib.client.OrderState;
import com.ib.client.OrderStatus;
import com.ib.client.OrderType;
import com.ib.client.Types;
import com.ib.controller.ApiController;
import lombok.Data;

import java.util.HashMap;
import java.util.Map;

@Data
public class OrderHandlerImpl implements ApiController.IOrderHandler {

    private ApiController client;
    private int permId;
    private Map<String/* code */, Integer/* orderId */> orderIdMap = Maps.newHashMap();
    private Map<String/* code */, Double/* avgPrice */> avgPriceMap = new HashMap<>();
    private Map<Integer/* orderId */, Order> orderIdToOrderMap = Maps.newHashMap();
    private Map<Integer/* orderId */, Contract> orderIdToContractMap = Maps.newHashMap();
    private Map<Integer/* permId */, Integer/* orderId */> permIdToOrderIdMap = Maps.newHashMap();
    private Map<Integer/* orderId */, OrderStatus> orderIdToStatusMap = Maps.newHashMap();
    private String code;

    @Override
    public void orderState(OrderState orderState) {
        System.out.println("orderState: " + orderState);
    }

    @Override
    public void orderStatus(OrderStatus status, Decimal filled, Decimal remaining, double avgFillPrice, int permId, int parentId, double lastFillPrice, int clientId, String whyHeld, double mktCapPrice) {
        System.out.println("orderStatus: " + " " + status + " " + filled + " " + remaining + " " + avgFillPrice + " " + permId + " " + parentId + " " + lastFillPrice + " " + clientId + " " + whyHeld + " " + mktCapPrice);
        this.permId = permId;
        if (status == OrderStatus.Submitted) {
            orderIdMap.put(code, permId);
        } else if (status == OrderStatus.Filled) {
            avgPriceMap.put(code, avgFillPrice);
        }

        Integer orderId = permIdToOrderIdMap.get(permId);
        orderIdToStatusMap.put(orderId, status);
    }

    @Override
    public void handle(int errorCode, String errorMsg) {
        System.out.println("order error: " + errorCode + " " + errorMsg);
    }

    public long placeNormalOrder(String code, double price, double count, Types.Action action) {
        Contract contract = new Contract();
        contract.localSymbol(code);
        contract.secType(Types.SecType.OPT);
        contract.exchange("SMART");

        Order order = new Order();
        order.action(action);
        order.lmtPrice(price);
        order.orderType(OrderType.LMT);
        order.totalQuantity(Decimal.get(count));

        this.code = code;
        client.placeOrModifyOrder(contract, order, this);
        int orderId = order.orderId();
        orderIdMap.put(code, orderId);
        orderIdToOrderMap.put(orderId, order);
        orderIdToContractMap.put(orderId, contract);

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

        client.placeOrModifyOrder(contract, order, this);
    }

    public Order getOrder(int orderId) {
        return orderIdToOrderMap.get(orderId);
    }

    public OrderStatus getOrderStatus(int orderId) {
        return orderIdToStatusMap.get(orderId);
    }
}
