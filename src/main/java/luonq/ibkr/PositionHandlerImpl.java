package luonq.ibkr;

import bean.StockPosition;
import com.google.common.collect.Maps;
import com.ib.client.Contract;
import com.ib.client.Decimal;
import com.ib.controller.ApiController;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.Map;

@Data
@Slf4j
public class PositionHandlerImpl implements ApiController.IPositionHandler {

    private ApiController client;
    private Map<String, StockPosition> positionMap = Maps.newHashMap();
    private boolean getPosition = false;

    @Override
    public void position(String account, Contract contract, Decimal pos, double avgCost) {
        //        System.out.println(EWrapperMsgGenerator.position(account, contract, pos, avgCost));
        BigDecimal count = pos.value();
        String code = contract.localSymbol();
        code = code.replaceAll(" ", "");

        if (count.compareTo(BigDecimal.ZERO) == 0) {
            if (StringUtils.isNotBlank(code)) {
                positionMap.remove(code);
                log.info("remove position. code={}", code);
            }
            return;
        }

        StockPosition stockPosition = positionMap.get(code);
        if (stockPosition == null) {
            stockPosition = new StockPosition();
            stockPosition.setStock(code);
            stockPosition.setCanSellQty(count.doubleValue());
            positionMap.put(code, stockPosition);
            log.info("init position. code={}\tcount={}", code, count.doubleValue());
        } else {
            stockPosition.setCanSellQty(count.doubleValue());
            log.info("set position count. code={}\tcount={}", code, count.doubleValue());
        }
    }

    @Override
    public void positionEnd() {
        System.out.println("positionEnd");
        getPosition = true;
    }

    public Map<String, StockPosition> getPosition() {
        while (true) {
            if (getPosition) {
                getPosition = false;
                return positionMap;
            }
        }
    }

    public void setAvgCost(String code, double costPrice) {
        StockPosition stockPosition = new StockPosition();
        stockPosition.setStock(code);
        stockPosition.setCostPrice(costPrice);

        positionMap.put(code, stockPosition);
    }
}
