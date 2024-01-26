package bean;

import lombok.Data;

import java.io.Serializable;

@Data
public class Bean implements Serializable {

    String date;
    private double open;
    private double close;
    private double high;
    private double low;
    private double dn;
    private double up;
    private double changePnt;
    private double lowDnDiffPnt;
    private double highCloseDiffPnt;
    private double openDnDiffPnt;
    private double openUpDiffPnt;
    private double closeUpDiffPnt;
    private int closeGreatOpen; // true=1 false=0
    private int closeLessOpen; // true=1 false=0

    public String toString() {
        return String.format("%s\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f", date, open, close, high, low, dn, lowDnDiffPnt, highCloseDiffPnt);
    }

}
