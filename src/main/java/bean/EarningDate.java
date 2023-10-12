package bean;

import lombok.AllArgsConstructor;
import lombok.Data;

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

    private String stock;
    private String date;
    private String earningType;
    private String actualDate;
}
