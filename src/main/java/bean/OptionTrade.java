package bean;

import lombok.Data;

@Data
public class OptionTrade {

    private String code;
    private String contractType;
    private double strikePriceDiffRatio;
    private double buy;
    private double sell;

    public String toString() {
        return code + "\t" + contractType + "\t" + buy + "\t" + sell;
    }
}
