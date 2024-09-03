package util;

import java.time.format.DateTimeFormatter;

/**
 * Created by Luonanqin on 2023/2/9.
 */
public final class Constants {
    public static final String SEPARATOR = System.getProperty("file.separator");
    public static final String USER_PATH = "/Users/Luonanqin/study/intellij_idea_workspaces/splan_data/";

    public static final String DAY_FORMATTER = "MM/dd/yyyy";
    public static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern(Constants.DAY_FORMATTER);
    public static final DateTimeFormatter DB_DATE_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    public static final String HIS_BASE_PATH = USER_PATH + "historicalData/";
    public static final String GRAB_PATH = HIS_BASE_PATH + "grab/";
    public static final String GRAB_ONE_YEAR_PATH = HIS_BASE_PATH + "graboneyear/";
    public static final String DAILY_PATH = HIS_BASE_PATH + "daily/";
    public static final String WEEKLY_PATH = HIS_BASE_PATH + "weekly/";
    public static final String FIX_WEEKLY_PATH = HIS_BASE_PATH + "fixWeekly/";

    public static final String STD_BASE_PATH = USER_PATH + "standardData/";
    public static final String STD_DAILY_PATH = STD_BASE_PATH + "daily/";
    public static final String STD_WEEKLY_PATH = STD_BASE_PATH + "weekly/";
    public static final String STD_MONTHLY_PATH = STD_BASE_PATH + "monthly/";
    public static final String STD_QUARTERLY_PATH = STD_BASE_PATH + "quarterly/";
    public static final String STD_YEARLY_PATH = STD_BASE_PATH + "yearly/";

    public static final String INDICATOR_BASE_PATH = USER_PATH + "indicatorData/";
    public static final String INDICATOR_MA_PATH = INDICATOR_BASE_PATH + "MA/";
    public static final String INDICATOR_BOLL_PATH = INDICATOR_BASE_PATH + "BOLL/";

    public static final String TRADE_PATH = USER_PATH + "tradeData/";
    public static final String TRADE_OPEN_PATH = TRADE_PATH + "open/";

    public static final String TEST_PATH = USER_PATH + "testData/";

    public static final String SPLIT_PATH = USER_PATH + "splitData/";

    public static int TRADE_ERROR_CODE = -500;
    public static int TRADE_PROHIBT_CODE = -600;

    public static double INIT_CASH = 1405;
}
