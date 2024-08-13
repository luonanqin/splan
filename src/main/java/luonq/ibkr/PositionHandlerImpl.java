package luonq.ibkr;

import com.google.common.collect.Maps;
import com.ib.client.Contract;
import com.ib.client.Decimal;
import com.ib.controller.ApiController;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;

import java.util.Map;

@Data
@Slf4j
public class PositionHandlerImpl implements ApiController.IPositionHandler {

    private Map<String, Double> positionCountMap = Maps.newHashMap();

    @Override
    public void position(String account, Contract contract, Decimal pos, double avgCost) {
        //        System.out.println(EWrapperMsgGenerator.position(account, contract, pos, avgCost));
        String code = contract.localSymbol();
        if (StringUtils.isBlank(code)) {
            return;
        }
        Double count = pos.value().doubleValue();
//        code = code.replaceAll(" ", "");
        positionCountMap.put(code, count);

        log.info("position info: code={}\tcount={}", code, count);
    }

    @Override
    public void positionEnd() {
        log.info("positionEnd");
    }

    public double getCanSellQty(String code) {
        return MapUtils.getDouble(positionCountMap, code, 0d);
    }
}
