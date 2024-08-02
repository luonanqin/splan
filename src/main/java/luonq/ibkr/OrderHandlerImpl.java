package luonq.ibkr;

import com.ib.client.Decimal;
import com.ib.client.OrderState;
import com.ib.client.OrderStatus;
import com.ib.controller.ApiController;
import lombok.Data;

@Data
public class OrderHandlerImpl implements ApiController.IOrderHandler {

    private ApiController client;
    private int permId;
//    private Map<String/* code */, Integer/* orderId */> orderIdMap = Maps.newHashMap();
//    private Map<String/* code */, Double/* avgPrice */> avgPriceMap = new HashMap<>();
//    private Map<Integer/* orderId */, Order> orderIdToOrderMap = Maps.newHashMap();
//    private Map<Integer/* orderId */, Contract> orderIdToContractMap = Maps.newHashMap();
//    private Map<Integer/* permId */, Integer/* orderId */> permIdToOrderIdMap = Maps.newHashMap();
//    private Map<Integer/* orderId */, OrderStatus> orderIdToStatusMap = Maps.newHashMap();
//    private Map<Integer/* permId */, OrderStatus> permIdToStatusMap = Maps.newHashMap();
    private String code;
    private OrderStatus status;
    private double avgPrice = 0;

    @Override
    public void orderState(OrderState orderState) {
//        System.out.println("orderState: " + orderState);
    }

    @Override
    public void orderStatus(OrderStatus status, Decimal filled, Decimal remaining, double avgFillPrice, int permId, int parentId, double lastFillPrice, int clientId, String whyHeld, double mktCapPrice) {
//        System.out.println("orderStatus: " + " " + status + " " + filled + " " + remaining + " " + avgFillPrice + " " + permId + " " + parentId + " " + lastFillPrice + " " + clientId + " " + whyHeld + " " + mktCapPrice);
        this.permId = permId;
        this.status = status;
        this.avgPrice = avgFillPrice;
    }

    @Override
    public void handle(int errorCode, String errorMsg) {
        System.out.println("order error: " + errorCode + " " + errorMsg);
    }
}
