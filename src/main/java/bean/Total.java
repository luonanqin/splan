package bean;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;

@Data
public class Total {

    private String code;
    private String dbYear;
    private String date;
    private double open;
    private double close;
    private double high;
    private double low;
    private BigDecimal volume;
    private double openTrade;
    private String openTradeTime;
    private double f1minAvgPrice;
    private double f1minVolume;
    private double md;
    private double mb;
    private double up;
    private double dn;
    private double openMd;
    private double openMb;
    private double openUp;
    private double openDn;
    private double ma5;
    private double ma10;
    private double ma20;
    private double ma30;
    private double ma60;

    public String getOpenTradeTime() {
        if (StringUtils.isBlank(openTradeTime)) {
            return "";
        }
        return openTradeTime;
    }
}
