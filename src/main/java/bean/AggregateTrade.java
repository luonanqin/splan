package bean;

import lombok.Data;

/**
 * Created by Luonanqin on 2023/4/28.
 */
@Data
public class AggregateTrade {

    private int v; // volume
    private double vw; // avg price
    private long t; // the timestamp for the start of the aggregate window.
    private double o; // open price
    private double c; // close price
    private double h; // highest price
    private double l; // lowest price

    public String toString() {
        return t + "," + vw + "," + o + "," + c + "," + h + "," + l + "," + v;
    }
}
