package util;

import java.time.format.DateTimeFormatter;

/**
 * Created by Luonanqin on 2023/2/9.
 */
public final class Constants {
    public static final String DAY_FORMATTER = "MM/dd/yyyy";
    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern(Constants.DAY_FORMATTER);

    public static final String BASE_PATH = "src/main/resources/historicalData/";
    public static final String GRAB_PATH = BASE_PATH + "grab/";
    public static final String GRAB_ONE_YEAR_PATH = "src/main/resources/historicalData/graboneyear/";
    public static final String DAILY_PATH = BASE_PATH + "daily/";
    public static final String WEEKLY_PATH = BASE_PATH + "weekly/";
    public static final String FIX_WEEKLY_PATH = "src/main/resources/historicalData/fixWeekly/";
}
