package bean;

import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.util.List;

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
    private String outPriceCallOptionCode_4; // 价外4档call
    private String outPricePutOptionCode_1; // 价外1档put
    private String outPricePutOptionCode_2; // 价外2档put
    private String outPricePutOptionCode_3; // 价外3档put
    private String outPricePutOptionCode_4; // 价外4档put

    private String inPriceCallOptionCode_1; // 价内1档call
    private String inPriceCallOptionCode_2; // 价内2档call
    private String inPriceCallOptionCode_3; // 价内3档call
    private String inPriceCallOptionCode_4; // 价内4档call
    private String inPricePutOptionCode_1; // 价内1档put
    private String inPricePutOptionCode_2; // 价内2档put
    private String inPricePutOptionCode_3; // 价内3档put
    private String inPricePutOptionCode_4; // 价内4档put

    private double outCall1StrikePrice; // 价外1档call行权价
    private double outCall2StrikePrice; // 价外2档call行权价
    private double outPut1StrikePrice; // 价外1档put行权价
    private double outPut2StrikePrice; // 价外2档put行权价

    private List<String> callList;
    private List<String> putList;

    public double getOutCall1StrikePrice() {
        return getStrikePrice(outPriceCallOptionCode_1);
    }

    public double getOutCall2StrikePrice() {
        return getStrikePrice(outPriceCallOptionCode_2);
    }

    public double getOutCall3StrikePrice() {
        return getStrikePrice(outPriceCallOptionCode_3);
    }

    public double getOutCall4StrikePrice() {
        return getStrikePrice(outPriceCallOptionCode_4);
    }

    public double getOutPut1StrikePrice() {
        return getStrikePrice(outPricePutOptionCode_1);
    }

    public double getOutPut2StrikePrice() {
        return getStrikePrice(outPricePutOptionCode_2);
    }

    public double getOutPut3StrikePrice() {
        return getStrikePrice(outPricePutOptionCode_3);
    }

    public double getOutPut4StrikePrice() {
        return getStrikePrice(outPricePutOptionCode_4);
    }

    public double getInCall1StrikePrice() {
        return getStrikePrice(inPriceCallOptionCode_1);
    }

    public double getInCall2StrikePrice() {
        return getStrikePrice(inPriceCallOptionCode_2);
    }

    public double getInCall3StrikePrice() {
        return getStrikePrice(inPriceCallOptionCode_3);
    }

    public double getInCall4StrikePrice() {
        return getStrikePrice(inPriceCallOptionCode_4);
    }

    public double getInPut1StrikePrice() {
        return getStrikePrice(inPricePutOptionCode_1);
    }

    public double getInPut2StrikePrice() {
        return getStrikePrice(inPricePutOptionCode_2);
    }

    public double getInPut3StrikePrice() {
        return getStrikePrice(inPricePutOptionCode_3);
    }

    public double getInPut4StrikePrice() {
        return getStrikePrice(inPricePutOptionCode_4);
    }

    private double getStrikePrice(String optionCode) {
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
