package util;

import java.time.format.DateTimeFormatter;

/**
 * Created by Luonanqin on 2023/2/9.
 */
public final class Constants {
    public static final String DAY_FORMATTER = "MM/dd/yyyy";
    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern(Constants.DAY_FORMATTER);
    public static final String TRADE_DAY_PATH = "src/main/resources/historicalData/tradeDay/";
    public static final String DAILY_PATH = "src/main/resources/historicalData/daily/";
}
