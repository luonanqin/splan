package bean;

import lombok.Data;

@Data
public class OptionStraddle {

    private String callCode;
    private String putCode;
    private Double callMidPrice;
    private Double putMidPrice;
    private double sumPrice;
}
