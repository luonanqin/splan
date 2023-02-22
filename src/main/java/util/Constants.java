package util;

import java.time.format.DateTimeFormatter;

/**
 * Created by Luonanqin on 2023/2/9.
 */
public final class Constants {
    public static final String DAY_FORMATTER = "MM/dd/yyyy";
    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern(Constants.DAY_FORMATTER);

    public static final String HIS_BASE_PATH = "src/main/resources/historicalData/";
    public static final String GRAB_PATH = HIS_BASE_PATH + "grab/";
    public static final String GRAB_ONE_YEAR_PATH = "src/main/resources/historicalData/graboneyear/";
    public static final String DAILY_PATH = HIS_BASE_PATH + "daily/";
    public static final String WEEKLY_PATH = HIS_BASE_PATH + "weekly/";
    public static final String FIX_WEEKLY_PATH = "src/main/resources/historicalData/fixWeekly/";

    public static final String STD_BASE_PATH = "src/main/resources/standardData/";
    public static final String STD_DAILY_PATH = STD_BASE_PATH + "daily/";
    public static final String STD_WEEKLY_PATH = STD_BASE_PATH + "weekly/";
    public static final String STD_MONTHLY_PATH = STD_BASE_PATH + "monthly/";
    public static final String STD_QUARTERLY_PATH = STD_BASE_PATH + "quarterly/";
    public static final String STD_YEARLY_PATH = STD_BASE_PATH + "yearly/";

}
