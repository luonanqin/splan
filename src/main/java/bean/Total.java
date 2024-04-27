package bean;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;

@Data
public class Total {

    private int id;
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

    public StockKLine toKLine() {
        return StockKLine.builder().code(code).date(date).open(open).close(close).high(high).low(low).volume(volume).build();
    }

    public BOLL toBoll() {
        return BOLL.builder().code(code).date(date).mb(mb).dn(dn).up(up).md(md).build();
    }

    public BOLL toOpenBoll() {
        return BOLL.builder().code(code).date(date).mb(openMb).dn(openDn).up(openUp).md(openMd).build();
    }

    public SimpleTrade toOpenTrade() {
        return SimpleTrade.builder().code(code).date(date).tradePrice(openTrade).tradeTime(openTradeTime).volume(f1minVolume).build();
    }

    public RealOpenVol toF1minInfo() {
        return RealOpenVol.builder().code(code).date(date).avgPrice(f1minAvgPrice).volume(f1minVolume).build();
    }
}
