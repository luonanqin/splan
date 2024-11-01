package bean;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;

@Data
public class NearlyOptionData {

    private String date;
    private String stock;
    private double openPrice;
    private String optionCode;
    private int strikePriceStep;
    private Double openStrikePriceDiffRatio;

    private String outPriceCallOptionCode_1; // 价外1档call
    private String outPriceCallOptionCode_2; // 价外2档call
    private String outPriceCallOptionCode_3; // 价外3档call
    private String outPricePutOptionCode_1; // 价外1档put
    private String outPricePutOptionCode_2; // 价外2档put
    private String outPricePutOptionCode_3; // 价外3档put

    private double outCall1StrikePrice; // 价外1档call行权价
    private double outCall2StrikePrice; // 价外2档call行权价
    private double outPut1StrikePrice; // 价外1档put行权价
    private double outPut2StrikePrice; // 价外2档put行权价

    public double getOutCall1StrikePrice() {
        return getOutStrikePrice(outPriceCallOptionCode_1);
    }

    public double getOutCall2StrikePrice() {
        return getOutStrikePrice(outPriceCallOptionCode_2);
    }

    public double getOutPut1StrikePrice() {
        return getOutStrikePrice(outPricePutOptionCode_1);
    }

    public double getOutPut2StrikePrice() {
        return getOutStrikePrice(outPricePutOptionCode_2);
    }

    private double getOutStrikePrice(String optionCode) {
        if (StringUtils.isBlank(optionCode)) {
            return 0;
        }
        String substring = optionCode.substring(optionCode.length() - 8);
        Integer strikePriceInt = Integer.valueOf(substring);
        return BigDecimal.valueOf(strikePriceInt).divide(BigDecimal.valueOf(1000)).doubleValue();
    }

    public static void main(String[] args) {
        NearlyOptionData nearlyOptionData = new NearlyOptionData();
        nearlyOptionData.setOutPriceCallOptionCode_1("O:TGT240105P00013500");
        System.out.println(nearlyOptionData.getOutCall1StrikePrice());
    }
}
