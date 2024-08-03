package luonq.ibkr;

import com.ib.client.Decimal;
import com.ib.client.OrderState;
import com.ib.client.OrderStatus;
import com.ib.controller.ApiController;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;

@Data
@Slf4j
public class OrderHandlerImpl implements ApiController.IOrderHandler {

    private int permId;
    private String code;
    private OrderStatus status;
    private double count;
    private double avgPrice = 0;
    private int orderId = 0;

    @Override
    public void orderState(OrderState orderState) {
        //        System.out.println("orderState: " + orderState);
    }

    @Override
    public void orderStatus(OrderStatus status, Decimal filled, Decimal remaining, double avgFillPrice, int permId, int parentId, double lastFillPrice, int clientId, String whyHeld, double mktCapPrice) {
        this.permId = permId;
        this.status = status;
        this.avgPrice = avgFillPrice;

        log.info("orderStatus: code={}\torderId={}\tstatus={}\tfilled={}\tremaining={}\tavgFillPrice={}\tpermId={}\tparentId={}\tlastFillPrice{}\tclientId={}\twhyHeld={}\tmktCapPrice={}",
          code, orderId, status, filled, remaining, avgFillPrice, permId, parentId, lastFillPrice, clientId, whyHeld, mktCapPrice);

    }

    @Override
    public void handle(int errorCode, String errorMsg) {
        log.error("order error: code={}\torderId={}\tpermId={}\terrorCode={}\terrorMsg={}", code, orderId, permId, errorCode, errorMsg);
    }
}
