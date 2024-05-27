package bean;

import lombok.Data;

@Data
public class NearlyOptionData {

    private String date;
    private double openPrice;
    private String optionCode;
    private int strikePriceStep;
    private Double openStrikePriceDiffRatio;

    private String outPriceCallOptionCode_1; // 价外1档call
    private String outPriceCallOptionCode_2; // 价外2档call
    private String outPricePutOptionCode_1; // 价外1档put
    private String outPricePutOptionCode_2; // 价外2档put
}
