package bean;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;

/**
 * Created by Luonanqin on 2023/1/31.
 */
@Data
@Builder
public class StockKLine {

    private String date;
    private double open;
    private double close;
    private double high;
    private double low;
    private double changePnt; // 涨跌百分比
    private BigDecimal volumn; // 成交量非成交额
}
