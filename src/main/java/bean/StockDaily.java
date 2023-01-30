package bean;

import lombok.Data;

/**
 * Created by Luonanqin on 2023/1/31.
 */
@Data
public class StockDaily {

    private double open;
    private double close;
    private double high;
    private double low;
    private double changePnt; // 涨跌百分比
    private double volumn; // 成交量非成交额
}
