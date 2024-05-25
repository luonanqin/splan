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
}
