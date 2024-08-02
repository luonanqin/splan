package luonq.ibkr;

import com.ib.controller.ApiController;
import lombok.Data;

@Data
public class OrderCancelHandlerImpl implements ApiController.IOrderCancelHandler {

    private ApiController client;
    private int orderId;

    @Override
    public void orderStatus(String orderStatus) {
        System.out.println("orderStatus: orderId=" + orderId + " status=" + orderStatus);
    }

    @Override
    public void handle(int errorCode, String errorMsg) {
        System.out.println("handle error: orderId=" + orderId + ". " + errorCode + " " + errorMsg);
    }
}
