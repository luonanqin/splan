package bean;

import lombok.Data;

@Data
public class OptionLastTrade {

    private long sip_timestamp;
    private double price;
    private int size;
}
