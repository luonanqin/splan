package bean;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import util.Constants;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.temporal.ChronoField;

/**
 * Created by Luonanqin on 2023/1/31.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StockKLine {

    private String date;
    private String code;
    private double open;
    private double close;
    private double high;
    private double low;
    private double lastClose; // 昨收
    private double change; // 涨跌金额
    private double changePnt; // 涨跌百分比
    //    private double volume; // 成交量非成交额
    private BigDecimal volume; // 成交量非成交额
    private String dbDate;

    public String getDbYear() {
        LocalDate oDate = LocalDate.parse(date, Constants.FORMATTER);
        return String.valueOf(oDate.get(ChronoField.YEAR));
    }

    public String getFormatDate(){
        LocalDate oDate = LocalDate.parse(date, Constants.FORMATTER);
        return oDate.format(Constants.DB_DATE_FORMATTER);
    }

    @Override
    public String toString() {
        return date + "," + open + "," + high + "," + low + "," + close + "," + change + "," + changePnt + "," + volume.toString();
    }
}
