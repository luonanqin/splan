package bean;

import lombok.Data;

@Data
public class TradeCalendar {

    private String date;
    private int type; // 0=全天交易 1=上午交易，下午休市 2=下午交易，上午休市
}
