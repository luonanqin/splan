package luonq.ibkr;

import com.ib.controller.AccountSummaryTag;
import com.ib.controller.ApiController;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import util.Constants;

@Slf4j
@Data
public class AccountSummaryHandlerImpl implements ApiController.IAccountSummaryHandler {

    private double cash = 0;

    @Override
    public void accountSummary(String account, AccountSummaryTag tag, String value, String currency) {
        log.info("accountSummary: {} {} {} {}", account, tag, value, currency);
        if (StringUtils.isNotBlank(value)) {
            try {
                double totalCash = Double.valueOf(value);
                cash = totalCash - 25000;
            } catch (Exception e) {
                log.error("get account cash error. ", e);
                cash = Constants.INIT_CASH;
            }
        } else {
            cash = Constants.INIT_CASH;
        }
    }

    @Override
    public void accountSummaryEnd() {
        log.info("accountSummaryEnd");
    }
}
