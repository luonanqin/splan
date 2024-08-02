package luonq.ibkr;

import com.ib.controller.ApiController;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class OrderCancelHandlerImpl implements ApiController.IOrderCancelHandler {

    private int orderId;
    private String code;

    @Override
    public void orderStatus(String orderStatus) {
        log.info("cancel order status: code={}\torderId={}\tstatus={}", code, orderId, orderStatus);
    }

    @Override
    public void handle(int errorCode, String errorMsg) {
        log.error("cancel order error: code={}\torderId={}\terrorCode={}\terrorMsg={}", code, orderId, errorCode, errorMsg);
    }
}
