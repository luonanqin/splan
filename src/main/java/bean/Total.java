package bean;

import lombok.Data;

import java.math.BigDecimal;

@Data
public class Total {

    private String code;
    private String date;
    private double open;
    private double close;
    private double high;
    private double low;
    private BigDecimal volume;
    private double openTrade;
    private double openTradeTime;
    private double f1minAvgPrice;
    private double f1minAvgVolume;
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
}
