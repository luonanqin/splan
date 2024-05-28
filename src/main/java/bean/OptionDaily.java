package bean;

import lombok.Data;

@Data
public class OptionDaily {

    private String status; // "OK",
    private String from; // "2024-01-05",
    private String symbol; // "O:AAPL240105P00180000",
    private double open; // 0.29,
    private double high; // 0.54,
    private double low; // 0.01,
    private double close; // 0.01,
    private long volume; // 107789,
    private double afterHours; // 0.01,
    private double preMarket; // 0.29

    public OptionDaily() {
    }

    public OptionDaily(String from, String symbol) {
        this.from = from;
        this.symbol = symbol;
    }

    public String toString() {
        return from + "\t" + symbol + "\t" + open + "\t" + high + "\t" + low + "\t" + close + "\t" + volume;
    }

    public static OptionDaily EMPTY(String from, String symbol) {
        OptionDaily d = new OptionDaily();
        d.setStatus("EMPTY");
        d.setFrom(from);
        d.setSymbol(symbol);
        d.setOpen(0);
        d.setClose(0);
        d.setHigh(0);
        d.setLow(0);
        d.setAfterHours(0);
        d.setPreMarket(0);
        d.setVolume(0);

        return d;
    }
}
