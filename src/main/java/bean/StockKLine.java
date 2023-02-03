package bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.ToString;

import java.math.BigDecimal;

/**
 * Created by Luonanqin on 2023/1/31.
 */
@Data
@Builder
@ToString
@NoArgsConstructor
@AllArgsConstructor
public class StockKLine {

    private String date;
    private double open;
    private double close;
    private double high;
    private double low;
    private double change; // 涨跌金额
    private double changePnt; // 涨跌百分比
//    private double volumn; // 成交量非成交额
    private BigDecimal volumn; // 成交量非成交额
}
