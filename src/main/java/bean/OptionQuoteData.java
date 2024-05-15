package bean;

import lombok.Data;

@Data
public class OptionQuoteData {

    private String code;
    private double openBuy;
    private double openSell;
    private double closeBuy;
    private double closeSell;

    public String toString() {
        return code + "\t" + openBuy + "\t" + openSell + "\t" + closeBuy + "\t" + closeSell;
    }
}
