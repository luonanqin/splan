package bean;

import lombok.Data;

@Data
public class OptionCode {

    private String code;
    private String contractType;
    private double strikePrice;
    private int digitalStrikePrice;
    private int actualDigitalStrikePrice;
    private int nextDigitalStrikePrice;

}
