package bean;

import lombok.AllArgsConstructor;
import lombok.Data;
import util.Constants;

import java.time.LocalDate;

/**
 * Created by Luonanqin on 2023/9/7.
 */
@AllArgsConstructor
@Data
public class EarningDate {

    public static final String TAS = "TAS";
    public static final String AFTER_MARKET_CLOSE = "After Market Close";
    public static final String TIME_NOT_SUPPLIED = "Time Not Supplied";
    public static final String BEFORE_MARKET_OPEN = "Before Market Open";
    public static final String DURING_MARKET_HOUR = "dmh";


    private String stock;
    private String date;
    private String earningType;
    private String actualDate;

    public String getActualDbDate() {
        return LocalDate.parse(actualDate, Constants.FORMATTER).format(Constants.DB_DATE_FORMATTER);
    }

    public String getDbDate() {
        return LocalDate.parse(date, Constants.FORMATTER).format(Constants.DB_DATE_FORMATTER);
    }
}
