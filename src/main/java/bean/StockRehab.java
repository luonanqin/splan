package bean;

import lombok.Data;

@Data
public class StockRehab {
    private String code;
    private String date;
    private double fwdFactorA;
    private long companyActFlag;

    // CMRE 2023-10-19 0.98764 64
    public String toString() {
        return String.format("%s %s %s %s", code, date, String.valueOf(fwdFactorA), String.valueOf(companyActFlag));
    }

    public static StockRehab convert(String line) {
        String[] split = line.split(" ");
        StockRehab stockRehab = new StockRehab();
        stockRehab.setCode(split[0]);
        stockRehab.setDate(split[1]);
        stockRehab.setFwdFactorA(Double.valueOf(split[2]));
        stockRehab.setCompanyActFlag(Long.valueOf(split[3]));

        return stockRehab;
    }
}
