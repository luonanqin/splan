package bean;

import lombok.Data;

@Data
public class AggregateOptionIV {

    private String timestamp;
    private int stockId;
    private String stockSymbol;
    private String optionExpirationDate;
    private double optionStrike;
    private String optionType;
    private String optionStyle;
    private String optionSymbol;
    private double optionBidPrice;
    private double optionAskPrice;
    private String optionBidDateTime;
    private String optionAskDateTime;
    private int optionBidSize;
    private int optionAskSize;
    private String optionBidExchange;
    private String optionAskExchange;
    private int optionVolume;
    private double optionIv;
    private double underlyingPrice;
    private double optionDelta;
    private double optionGamma;
    private double optionTheta;
    private double optionVega;
    private double optionRho;
    private double optionPreIv;
    private double optionImpliedYield;
    private String calcTimestamp;
}
