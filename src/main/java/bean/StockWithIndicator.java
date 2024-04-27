package bean;

import com.alibaba.fastjson.JSON;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Created by Luonanqin on 2023/1/31.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockWithIndicator {

    private String date;
    private double open;
    private double close;
    private double high;
    private double low;
    private double change; // 涨跌金额
    private BigDecimal volume; // 成交量非成交额
    private double ma5;
    private double ma10;
    private double ma20;
    private double ma30;
    private double ma60;
    private double bollUpper;
    private double bollMiddle;
    private double bollLower;

    @Override
    public String toString() {
        return JSON.toJSONString(this);
    }
}
