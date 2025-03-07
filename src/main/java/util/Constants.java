package util;

import com.google.common.collect.Sets;

import java.time.format.DateTimeFormatter;
import java.util.Set;

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

    /* 2024 */
        public static int[] weekArray = new int[] { 20240105, 20240112, 20240119, 20240126, 20240202, 20240209, 20240216, 20240223, 20240301, 20240308, 20240315, 20240322, 20240328, 20240405, 20240412, 20240419, 20240426, 20240503, 20240510, 20240517, 20240524, 20240531, 20240607, 20240614, 20240621, 20240628, 20240705, 20240712, 20240719, 20240726, 20240802, 20240809, 20240816, 20240823, 20240830, 20240906, 20240913, 20240920, 20240927, 20241004, 20241011, 20241018, 20241025, 20241101, 20241108, 20241115, 20241122, 20241129, 20241206, 20241213};
        public static String[] weekStrArray = new String[] { "2024-01-05", "2024-01-12", "2024-01-19", "2024-01-26", "2024-02-02", "2024-02-09", "2024-02-16", "2024-02-23", "2024-03-01", "2024-03-08", "2024-03-15", "2024-03-22", "2024-03-28", "2024-04-05", "2024-04-12", "2024-04-19", "2024-04-26", "2024-05-03", "2024-05-10", "2024-05-17", "2024-05-24", "2024-05-31", "2024-06-07", "2024-06-14", "2024-06-21", "2024-06-28", "2024-07-05", "2024-07-12", "2024-07-19", "2024-07-26", "2024-08-02", "2024-08-09", "2024-08-16", "2024-08-23", "2024-08-30", "2024-09-06", "2024-09-13", "2024-09-20", "2024-09-27", "2024-10-04", "2024-10-11", "2024-10-18", "2024-10-25", "2024-11-01", "2024-11-08", "2024-11-15", "2024-11-22", "2024-11-29", "2024-12-06", "2024-12-13"};
        public static Set<String> weekSet = Sets.newHashSet("2024-01-05", "2024-01-12", "2024-01-19", "2024-01-26", "2024-02-02", "2024-02-09", "2024-02-16", "2024-02-23", "2024-03-01", "2024-03-08", "2024-03-15", "2024-03-22", "2024-03-28", "2024-04-05", "2024-04-12", "2024-04-19", "2024-04-26", "2024-05-03", "2024-05-10", "2024-05-17", "2024-05-24", "2024-05-31", "2024-06-07", "2024-06-14", "2024-06-21", "2024-06-28", "2024-07-05", "2024-07-12", "2024-07-19", "2024-07-26", "2024-08-02", "2024-08-09", "2024-08-16", "2024-08-23", "2024-08-30", "2024-09-06", "2024-09-13", "2024-09-20", "2024-09-27", "2024-10-04", "2024-10-11", "2024-10-18", "2024-10-25", "2024-11-01", "2024-11-08", "2024-11-15", "2024-11-22", "2024-11-29", "2024-12-06", "2024-12-13");
        public static int year = 2024;
        public static Set<String> invalidDay = Sets.newHashSet("2024-01-04", "2024-01-08", "2024-01-09", "2024-01-10", "2024-01-11", "2024-01-16", "2024-01-17", "2024-01-22", "2024-01-23", "2024-01-24", "2024-01-25", "2024-01-29", "2024-01-30", "2024-01-31", "2024-02-01", "2024-02-02", "2024-02-05", "2024-02-06", "2024-02-07", "2024-02-08", "2024-02-09", "2024-02-13", "2024-02-14", "2024-02-15", "2024-02-16", "2024-02-20", "2024-02-21", "2024-02-22", "2024-02-23", "2024-02-27", "2024-02-28", "2024-02-29", "2024-03-04", "2024-03-05", "2024-03-06", "2024-03-07", "2024-03-08", "2024-03-11", "2024-03-12", "2024-03-14", "2024-03-18", "2024-03-19", "2024-03-20", "2024-03-21", "2024-03-25", "2024-03-26", "2024-03-27", "2024-04-02", "2024-04-03", "2024-04-04", "2024-04-08", "2024-04-09", "2024-04-10", "2024-04-15", "2024-04-17", "2024-04-18", "2024-04-19", "2024-04-22", "2024-04-23", "2024-04-24", "2024-04-25", "2024-04-26", "2024-04-29", "2024-04-30", "2024-05-01", "2024-05-02", "2024-05-03", "2024-05-06", "2024-05-07", "2024-05-08", "2024-05-09", "2024-05-13", "2024-05-14", "2024-05-15", "2024-05-17", "2024-05-20", "2024-05-21", "2024-05-23", "2024-05-29", "2024-05-30", "2024-06-03", "2024-06-04", "2024-06-06", "2024-06-07", "2024-06-12", "2024-06-13", "2024-06-14", "2024-06-18", "2024-06-21", "2024-06-24", "2024-06-26", "2024-06-27", "2024-06-28", "2024-07-01", "2024-07-02", "2024-07-09", "2024-07-11", "2024-07-15", "2024-07-17", "2024-07-18", "2024-07-22", "2024-07-23", "2024-07-24", "2024-07-25", "2024-07-26", "2024-07-29", "2024-07-30", "2024-07-31", "2024-08-01", "2024-08-02", "2024-08-05", "2024-08-06", "2024-08-07", "2024-08-08", "2024-08-09", "2024-08-13", "2024-08-14", "2024-08-15", "2024-08-19", "2024-08-21", "2024-08-26", "2024-08-28", "2024-08-29", "2024-09-05", "2024-09-09", "2024-09-11", "2024-09-13", "2024-09-16", "2024-09-17", "2024-09-19", "2023-09-20", "2024-09-23", "2024-09-25", "2024-09-26", "2024-09-27");
    /* 2023*/
//    public static int[] weekArray = new int[] { 20230106, 20230113, 20230120, 20230127, 20230203, 20230210, 20230217, 20230224, 20230303, 20230310, 20230317, 20230324, 20230331, 20230406, 20230414, 20230421, 20230428, 20230505, 20230512, 20230519, 20230526, 20230602, 20230609, 20230616, 20230623, 20230630, 20230707, 20230714, 20230721, 20230728, 20230804, 20230811, 20230818, 20230825, 20230901, 20230908, 20230915, 20230922, 20230929, 20231006, 20231013, 20231020, 20231027, 20231103, 20231110, 20231117, 20231124, 20231201, 20231208, 20231215, 20231222, 20231229 };
//    public static String[] weekStrArray = new String[] { "2023-01-06", "2023-01-13", "2023-01-20", "2023-01-27", "2023-02-03", "2023-02-10", "2023-02-17", "2023-02-24", "2023-03-03", "2023-03-10", "2023-03-17", "2023-03-24", "2023-03-31", "2023-04-06", "2023-04-14", "2023-04-21", "2023-04-28", "2023-05-05", "2023-05-12", "2023-05-19", "2023-05-26", "2023-06-02", "2023-06-09", "2023-06-16", "2023-06-23", "2023-06-30", "2023-07-07", "2023-07-14", "2023-07-21", "2023-07-28", "2023-08-04", "2023-08-11", "2023-08-18", "2023-08-25", "2023-09-01", "2023-09-08", "2023-09-15", "2023-09-22", "2023-09-29", "2023-10-06", "2023-10-13", "2023-10-20", "2023-10-27", "2023-11-03", "2023-11-10", "2023-11-17", "2023-11-24", "2023-12-01", "2023-12-08", "2023-12-15", "2023-12-22", "2023-12-29" };
//    public static Set<String> weekSet = Sets.newHashSet("2023-01-06", "2023-01-13", "2023-01-20", "2023-01-27", "2023-02-03", "2023-02-10", "2023-02-17", "2023-02-24", "2023-03-03", "2023-03-10", "2023-03-17", "2023-03-24", "2023-03-31", "2023-04-06", "2023-04-14", "2023-04-21", "2023-04-28", "2023-05-05", "2023-05-12", "2023-05-19", "2023-05-26", "2023-06-02", "2023-06-09", "2023-06-16", "2023-06-23", "2023-06-30", "2023-07-07", "2023-07-14", "2023-07-21", "2023-07-28", "2023-08-04", "2023-08-11", "2023-08-18", "2023-08-25", "2023-09-01", "2023-09-08", "2023-09-15", "2023-09-22", "2023-09-29", "2023-10-06", "2023-10-13", "2023-10-20", "2023-10-27", "2023-11-03", "2023-11-10", "2023-11-17", "2023-11-24", "2023-12-01", "2023-12-08", "2023-12-15", "2023-12-22", "2023-12-29");
//    public static int year = 2023;
//    public static Set<String> invalidDay = Sets.newHashSet("2023-01-05", "2023-01-09", "2023-01-12", "2023-01-13", "2023-01-18", "2023-01-19", "2023-01-20", "2023-01-23", "2023-01-24", "2023-01-25", "2023-01-26", "2023-01-27", "2023-01-30", "2023-01-31", "2023-02-01", "2023-02-02", "2023-02-03", "2023-02-06", "2023-02-07", "2023-02-08", "2023-02-09", "2023-02-14", "2023-02-15", "2023-02-16", "2023-02-17", "2023-02-21", "2023-02-22", "2023-02-23", "2023-02-24", "2023-02-27", "2023-02-28", "2023-03-01", "2023-03-02", "2023-03-03", "2023-03-07", "2023-03-08", "2023-03-09", "2023-03-13", "2023-03-14", "2023-03-15", "2023-03-17", "2023-03-20", "2023-03-21", "2023-03-23", "2023-03-24", "2023-03-27", "2023-03-29", "2023-04-03", "2023-04-04", "2023-04-05", "2023-04-11", "2023-04-12", "2023-04-13", "2023-04-14", "2023-04-17", "2023-04-18", "2023-04-19", "2023-04-20", "2023-04-21", "2023-04-25", "2023-04-26", "2023-04-27", "2023-05-01", "2023-05-03", "2023-05-04", "2023-05-05", "2023-05-08", "2023-05-09", "2023-05-10", "2023-05-11", "2023-05-15", "2023-05-16", "2023-05-17", "2023-05-18", "2023-05-23", "2023-05-24", "2023-05-25", "2023-05-26", "2023-05-30", "2023-05-31", "2023-06-01", "2023-06-02", "2023-06-06", "2023-06-07", "2023-06-08", "2023-06-09", "2023-06-12", "2023-06-13", "2023-06-14", "2023-06-15", "2023-06-16", "2023-06-20", "2023-06-21", "2023-06-22", "2023-06-23", "2023-06-26", "2023-06-27", "2023-06-30", "2023-07-03", "2023-07-05", "2023-07-06", "2023-07-07", "2023-07-10", "2023-07-11", "2023-07-12", "2023-07-13", "2023-07-14", "2023-07-19", "2023-07-20", "2023-07-25", "2023-07-26", "2023-07-27", "2023-07-28", "2023-07-31", "2023-08-01", "2023-08-02", "2023-08-03", "2023-08-04", "2023-08-08", "2023-08-09", "2023-08-11", "2023-08-14", "2023-08-16", "2023-08-17", "2023-08-18", "2023-08-21", "2023-08-22", "2023-08-24", "2023-08-25", "2023-08-29", "2023-08-30", "2023-08-31", "2023-09-01", "2023-09-07", "2023-09-08", "2023-09-11", "2023-09-12", "2023-09-14", "2023-09-21", "2023-09-26", "2023-09-27", "2023-09-28", "2023-10-05", "2023-10-10", "2023-10-11", "2023-10-12", "2023-10-16", "2023-10-18", "2023-10-19", "2023-10-20", "2023-10-24", "2023-10-25", "2023-10-27", "2023-10-30", "2023-11-02", "2023-11-03", "2023-11-08", "2023-11-09", "2023-11-10", "2023-11-14", "2023-11-15", "2023-11-16", "2023-11-21", "2023-11-22", "2023-11-27", "2023-11-28", "2023-11-29", "2023-11-30", "2023-12-04", "2023-12-05", "2023-12-06", "2023-12-07", "2023-12-11", "2023-12-12", "2023-12-13", "2023-12-14", "2023-12-15", "2023-12-19", "2023-12-20", "2023-12-21", "2023-12-27", "2023-12-28");
    /* 2022 */
    //            public static int[] weekArray = new int[] { 20220107, 20220114, 20220121, 20220128, 20220204, 20220211, 20220218, 20220225, 20220304, 20220311, 20220318, 20220325, 20220401, 20220408, 20220414, 20220422, 20220429, 20220506, 20220513, 20220520, 20220527, 20220603, 20220610, 20220617, 20220624, 20220701, 20220708, 20220715, 20220722, 20220729, 20220805, 20220812, 20220819, 20220826, 20220902, 20220909, 20220916, 20220923, 20220930, 20221007, 20221014, 20221021, 20221028, 20221104, 20221111, 20221118, 20221125, 20221202, 20221209, 20221216, 20221223, 20221230 };
    //            public static String[] weekStrArray = new String[] { "2022-01-07", "2022-01-14", "2022-01-21", "2022-01-28", "2022-02-04", "2022-02-11", "2022-02-18", "2022-02-25", "2022-03-04", "2022-03-11", "2022-03-18", "2022-03-25", "2022-04-01", "2022-04-08", "2022-04-14", "2022-04-22", "2022-04-29", "2022-05-06", "2022-05-13", "2022-05-20", "2022-05-27", "2022-06-03", "2022-06-10", "2022-06-17", "2022-06-24", "2022-07-01", "2022-07-08", "2022-07-15", "2022-07-22", "2022-07-29", "2022-08-05", "2022-08-12", "2022-08-19", "2022-08-26", "2022-09-02", "2022-09-09", "2022-09-16", "2022-09-23", "2022-09-30", "2022-10-07", "2022-10-14", "2022-10-21", "2022-10-28", "2022-11-04", "2022-11-11", "2022-11-18", "2022-11-25", "2022-12-02", "2022-12-09", "2022-12-16", "2022-12-23", "2022-12-30" };
    //            public static Set<String> weekSet = Sets.newHashSet("2022-01-07", "2022-01-14", "2022-01-21", "2022-01-28", "2022-02-04", "2022-02-11", "2022-02-18", "2022-02-25", "2022-03-04", "2022-03-11", "2022-03-18", "2022-03-25", "2022-04-01", "2022-04-08", "2022-04-14", "2022-04-22", "2022-04-29", "2022-05-06", "2022-05-13", "2022-05-20", "2022-05-27", "2022-06-03", "2022-06-10", "2022-06-17", "2022-06-24", "2022-07-01", "2022-07-08", "2022-07-15", "2022-07-22", "2022-07-29", "2022-08-05", "2022-08-12", "2022-08-19", "2022-08-26", "2022-09-02", "2022-09-09", "2022-09-16", "2022-09-23", "2022-09-30", "2022-10-07", "2022-10-14", "2022-10-21", "2022-10-28", "2022-11-04", "2022-11-11", "2022-11-18", "2022-11-25", "2022-12-02", "2022-12-09", "2022-12-16", "2022-12-23", "2022-12-30");
    //            public static int year = 2022;
    //            public static Set<String> invalidDay = Sets.newHashSet("2022-01-01","2022-01-02","2022-01-03","2022-01-04","2022-01-05","2022-01-06","2022-01-07","2022-01-08","2022-01-09","2022-01-10","2022-01-11","2022-01-12","2022-01-13","2022-01-14","2022-01-15","2022-01-16","2022-01-17","2022-01-18","2022-01-19","2022-01-20","2022-01-21","2022-01-22","2022-01-23","2022-01-24","2022-01-25","2022-01-26","2022-01-27","2022-01-28","2022-01-29","2022-01-30","2022-01-31","2022-02-01","2022-02-02","2022-02-03","2022-02-04","2022-02-05","2022-02-06","2022-02-07","2022-02-08","2022-02-09","2022-02-10","2022-02-11","2022-02-12","2022-02-13","2022-02-14","2022-02-15","2022-02-16","2022-02-17","2022-02-18","2022-02-19","2022-02-20","2022-02-21","2022-02-22","2022-02-23","2022-02-24","2022-02-25","2022-02-26","2022-02-27","2022-02-28", "2022-03-07", "2022-03-08", "2022-03-09", "2022-03-10", "2022-03-11", "2022-03-15", "2022-03-16", "2022-03-17", "2022-03-22", "2022-03-23", "2022-03-24", "2022-03-25", "2022-03-28", "2022-03-29", "2022-03-31", "2022-04-01", "2022-04-06", "2022-04-11", "2022-04-12", "2022-04-13", "2022-04-19", "2022-04-20", "2022-04-21", "2022-04-22", "2022-04-25", "2022-04-26", "2022-04-27", "2022-04-29", "2022-05-02", "2022-05-03", "2022-05-04", "2022-05-05", "2022-05-06", "2022-05-09", "2022-05-10", "2022-05-11", "2022-05-12", "2022-05-13", "2022-05-17", "2022-05-18", "2022-05-19", "2022-05-24", "2022-05-25", "2022-05-26", "2022-05-31", "2022-06-01", "2022-06-03", "2022-06-06", "2022-06-07", "2022-06-08", "2022-06-09", "2022-06-10", "2022-06-13", "2022-06-14", "2022-06-15", "2022-06-16", "2022-06-21", "2022-06-22", "2022-06-23", "", "2022-06-24", "2022-06-27", "2022-06-28", "2022-06-29", "2022-06-30", "2022-07-05", "2022-07-06", "2022-07-07", "2022-07-08", "2022-07-11", "2022-07-12", "2022-07-13", "2022-07-14", "2022-07-18", "2022-07-19", "2022-07-20", "2022-07-21", "2022-07-22", "2022-07-25", "2022-07-26", "2022-07-27", "2022-07-28", "2022-07-29", "2022-08-01", "2022-08-02", "2022-08-03", "2022-08-04", "2022-08-05", "2022-08-08", "2022-08-09", "2022-08-10", "2022-08-11", "2022-08-12", "2022-08-15", "2022-08-16", "2022-08-17", "2022-08-18", "2022-08-19", "2022-08-22", "2022-08-23", "2022-08-24", "2022-08-25", "2022-08-31", "2022-09-01", "2022-09-02", "2022-09-06", "2022-09-07", "2022-09-08", "2022-09-12", "2022-09-13", "2022-09-14", "2022-09-15", "2022-09-16", "2022-09-20", "2022-09-22", "2022-09-23", "2022-09-26", "2022-09-27", "2022-09-28", "2022-09-29", "2022-09-30", "2022-10-04", "2022-10-05", "2022-10-07", "2022-10-13", "2022-10-14", "2022-10-18", "2022-10-19", "2022-10-20", "2022-10-21", "2022-10-26", "2022-10-27", "2022-10-28", "2022-10-31", "2022-11-01", "2022-11-02", "2022-11-03", "2022-11-04", "2022-11-07", "2022-11-08", "2022-11-09", "2022-11-10", "2022-11-11", "2022-11-14", "2022-11-15", "2022-11-16", "2022-11-17", "2022-11-18", "2022-11-21", "2022-11-22", "2022-11-25", "2022-11-28", "2022-11-29", "2022-11-30", "2022-12-01", "2022-12-02", "2022-12-05", "2022-12-07", "2022-12-08", "2022-12-09", "2022-12-12", "2022-12-13", "2022-12-14", "2022-12-15", "2022-12-20", "2022-12-22", "2022-12-27", "2022-12-29");
}
