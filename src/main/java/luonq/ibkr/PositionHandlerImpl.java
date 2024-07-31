package luonq.ibkr;

import bean.StockPosition;
import com.google.common.collect.Maps;
import com.ib.client.Contract;
import com.ib.client.Decimal;
import com.ib.client.EWrapperMsgGenerator;
import com.ib.controller.ApiController;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Map;

@Data
public class PositionHandlerImpl implements ApiController.IPositionHandler {

    private ApiController client;
    private Map<String, StockPosition> positionMap = Maps.newHashMap();
    private boolean getPosition = false;

    @Override
    public void position(String account, Contract contract, Decimal pos, double avgCost) {
        System.out.println(EWrapperMsgGenerator.position(account, contract, pos, avgCost));
        BigDecimal count = pos.value();
        if (count.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        String code = contract.localSymbol();
        code = code.replaceAll(" ", "");

        StockPosition stockPosition = new StockPosition();
        stockPosition.setCanSellQty(count.doubleValue());

        positionMap.put(code, stockPosition);
    }

    @Override
    public void positionEnd() {
        System.out.println("positionEnd");
        getPosition = true;
    }

    public Map<String, StockPosition> getPosition() {
        positionMap.clear();

        client.reqPositions(this);
        while (true) {
            if (getPosition) {
                getPosition = false;
                return positionMap;
            }
        }
    }
}
