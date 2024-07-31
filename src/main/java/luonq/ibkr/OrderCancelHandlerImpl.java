package luonq.ibkr;

import com.ib.controller.ApiController;
import lombok.Data;

@Data
public class OrderCancelHandlerImpl implements ApiController.IOrderCancelHandler {

    private ApiController client;

    @Override
    public void orderStatus(String orderStatus) {
        System.out.println("orderStatus: " + orderStatus);
    }

    @Override
    public void handle(int errorCode, String errorMsg) {
        System.out.println("handle: " + errorCode + " " + errorMsg);
    }

    public void cancelOrder(int orderId) {
        client.cancelOrder(orderId, "", this);
    }
}
