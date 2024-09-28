package luonq.ibkr;

import com.ib.client.Decimal;
import com.ib.controller.ApiController;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;

@Getter
@Slf4j
public class PnLSingleHandlerImpl implements ApiController.IPnLSingleHandler {

    private double value = 0;

    @Override
    public void pnlSingle(int reqId, Decimal pos, double dailyPnL, double unrealizedPnL, double realizedPnL, double value) {
        this.value = value;
        log.info(reqId + "\t" + pos + "\t" + dailyPnL + "\t" + unrealizedPnL + "\t" + realizedPnL + "\t" + value);
    }
}
