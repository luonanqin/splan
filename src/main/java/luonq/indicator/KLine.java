package luonq.indicator;

import bean.StockKLine;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import luonq.test.StockDailyTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

import static util.Constants.DAY_FORMATTER;

/**
 * Created by Luonanqin on 2023/2/1.
 */
public class KLine {

    // 历史月k线
    public List<StockKLine> historicalMonthKLine(List<StockKLine> weekList) {
        List<StockKLine> week = Lists.newArrayList();

        //        boolean startNewWeek = false;
        String weekDate = null;
        double weekOpen = 0, weekClose = 0, weekHigh = 0, weekLow = 1000000;
        BigDecimal weekVolumn = BigDecimal.ZERO;

        for (int i = weekList.size() - 1; i >= 0; i--) {
            StockKLine daily = weekList.get(i);
            String date = daily.getDate();
            double open = daily.getOpen();
            double close = daily.getClose();
            double high = daily.getHigh();
            double low = daily.getLow();
            BigDecimal volume = daily.getVolume();

            LocalDate localDate = LocalDate.parse(date, DateTimeFormatter.ofPattern(DAY_FORMATTER));
            int dayOfWeek = localDate.getDayOfWeek().getValue();

            // 后一天是周一 或者 后一天和前一天之间有没有周末
            if (dayOfWeek == 1 && StringUtils.isNotBlank(weekDate)) {
                StockKLine kLine = StockKLine.builder().date(weekDate).open(weekOpen).close(weekClose).high(weekHigh).low(weekLow).volume(weekVolumn).build();
                week.add(kLine);

                weekOpen = 0;
                weekClose = 0;
                weekHigh = 0;
                weekLow = 1000000;
                weekVolumn = BigDecimal.ZERO;
            } else {
                if (weekOpen == 0) {
                    weekOpen = open;
                    weekDate = date;
                }
                if (high > weekHigh) {
                    weekHigh = high;
                }
                if (low < weekLow) {
                    weekLow = low;
                }
                weekClose = close;
                weekVolumn = weekVolumn.add(volume);
            }
        }

        return Lists.reverse(week);
    }

    // 历史年k线
    public List<StockKLine> historicalYearKLine(List<StockKLine> monthlyList) {
        List<StockKLine> yearList = Lists.newArrayList();

        String yearDate = null;
        double yearOpen = 0, yearClose = 0, yearHigh = 0, yearLow = 1000000;
        BigDecimal yearVolumn = BigDecimal.ZERO;

        for (int i = 0; i < monthlyList.size(); i++) {
            StockKLine monthly = monthlyList.get(i);
            String date = monthly.getDate();
            double open = monthly.getOpen();
            double close = monthly.getClose();
            double high = monthly.getHigh();
            double low = monthly.getLow();
            BigDecimal volume = monthly.getVolume();

            LocalDate localDate = LocalDate.parse(date, DateTimeFormatter.ofPattern(DAY_FORMATTER));
            int month = localDate.getMonthValue();

            if (month == 12 && StringUtils.isNotBlank(yearDate)) {
                StockKLine kLine = StockKLine.builder().date(yearDate).open(yearOpen).close(yearClose).high(yearHigh).low(yearLow).volume(yearVolumn).build();
                yearList.add(kLine);

                yearOpen = 0;
                yearClose = 0;
                yearHigh = 0;
                yearLow = 1000000;
                yearVolumn = BigDecimal.ZERO;
            }

            if (yearClose == 0) {
                yearClose = close;
                localDate = localDate.withMonth(1).withDayOfYear(1);
                yearDate = localDate.format(DateTimeFormatter.ofPattern(DAY_FORMATTER));
            }
            if (high > yearHigh) {
                yearHigh = high;
            }
            if (low < yearLow) {
                yearLow = low;
            }
            yearOpen = open;
            yearVolumn = yearVolumn.add(volume);

            if (i + 1 == monthlyList.size()) {
                StockKLine kLine = StockKLine.builder().date(yearDate).open(yearOpen).close(yearClose).high(yearHigh).low(yearLow).volume(yearVolumn).build();
                yearList.add(kLine);
            }
        }
        return yearList;
    }

    // 实时周k线
    public StockKLine realtimeWeekKLine(StockKLine latestWeekly, StockKLine latestDaily) {
        String weeklyDate = latestWeekly.getDate();
        String dailyDate = latestDaily.getDate();

        LocalDate weeklyParse = LocalDate.parse(weeklyDate, DateTimeFormatter.ofPattern(DAY_FORMATTER));
        LocalDate dailyParse = LocalDate.parse(dailyDate, DateTimeFormatter.ofPattern(DAY_FORMATTER));

        int weeklyDayOfYear = weeklyParse.getDayOfYear();
        int dailyDayOfYear = dailyParse.getDayOfYear();

        // 如果最新天数比最新一周的周一天数小，说明最新天为新的一年，需要判断是否闰年后再计算是否是新一周
        if (dailyDayOfYear < weeklyDayOfYear) {
            int year = weeklyParse.getYear();
            dailyDayOfYear += 365;
            if ((year % 100 == 0 && year % 400 == 0) || (year % 100 != 0 && year % 4 == 0)) { // 闰年多加1天
                dailyDayOfYear += 1;
            }
        }

        if (dailyDayOfYear - weeklyDayOfYear > 4) { // this is new weekly
            int dayOfWeek = dailyParse.getDayOfWeek().getValue();
            int dayOfMonth = dailyParse.getDayOfMonth();
            if (dayOfWeek > 1) {
                dayOfWeek = dayOfMonth - dayOfWeek + 1;
                dailyParse = dailyParse.withDayOfMonth(dayOfWeek);
                latestDaily.setDate(dailyParse.format(DateTimeFormatter.ofPattern(DAY_FORMATTER)));
            }
            return latestDaily;
        } else {
            StockKLine latestKLine = StockKLine.builder()
              .date(latestWeekly.getDate())
              .open(latestWeekly.getOpen())
              .close(latestDaily.getClose())
              .high(latestWeekly.getHigh())
              .low(latestWeekly.getLow())
              .volume(latestWeekly.getVolume().add(latestDaily.getVolume()))
              .build();
            if (latestDaily.getHigh() > latestWeekly.getHigh()) {
                latestKLine.setHigh(latestDaily.getHigh());
            }
            if (latestDaily.getLow() < latestWeekly.getLow()) {
                latestKLine.setLow(latestDaily.getLow());
            }
            return latestKLine;
        }
    }

    // 实时月k线
    public StockKLine realtimeMonthKLine(StockKLine latestMonthly, StockKLine latestDaily) {
        String monthlyDate = latestMonthly.getDate();
        String dailyDate = latestDaily.getDate();

        LocalDate monthlyParse = LocalDate.parse(monthlyDate, DateTimeFormatter.ofPattern(DAY_FORMATTER));
        LocalDate dailyParse = LocalDate.parse(dailyDate, DateTimeFormatter.ofPattern(DAY_FORMATTER));

        int latestMonth = monthlyParse.getMonthValue();
        int dailyMonth = dailyParse.getMonthValue();

        // 如果所在月份不一样，则最新天就是最新月
        if (dailyMonth != latestMonth) {
            String[] split = dailyDate.split("/");
            latestDaily.setDate(String.format("%s/%s/%s", split[0], "01", split[2]));
            return latestDaily;
        } else {
            StockKLine latestKLine = StockKLine.builder()
              .date(latestMonthly.getDate())
              .open(latestMonthly.getOpen())
              .close(latestDaily.getClose())
              .high(latestMonthly.getHigh())
              .low(latestMonthly.getLow())
              .volume(latestMonthly.getVolume().add(latestDaily.getVolume()))
              .build();
            if (latestDaily.getHigh() > latestMonthly.getHigh()) {
                latestKLine.setHigh(latestDaily.getHigh());
            }
            if (latestDaily.getLow() < latestMonthly.getLow()) {
                latestKLine.setLow(latestDaily.getLow());
            }
            return latestKLine;
        }
    }

    // 实时季k线
    public StockKLine realtimeQuarterKLine(StockKLine latestQuarterly, StockKLine latestDaily) {
        String quarterlyDate = latestQuarterly.getDate();
        String dailyDate = latestDaily.getDate();

        LocalDate quarterlyParse = LocalDate.parse(quarterlyDate, DateTimeFormatter.ofPattern(DAY_FORMATTER));
        LocalDate dailyParse = LocalDate.parse(dailyDate, DateTimeFormatter.ofPattern(DAY_FORMATTER));

        int latestMonth = quarterlyParse.getMonthValue();
        int dailyMonth = dailyParse.getMonthValue();

        // 如果最新月大于最新日所在月，则说明是新一年的第一季度
        // 如果最新日所在月大于最新月三个月，则说明是新的一季度
        if (dailyMonth < latestMonth || dailyMonth >= latestMonth + 3) {
            String[] split = dailyDate.split("/");
            latestDaily.setDate(String.format("%s/%s/%s", split[0], "01", split[2]));
            return latestDaily;
        } else {
            StockKLine latestKLine = StockKLine.builder()
              .date(latestQuarterly.getDate())
              .open(latestQuarterly.getOpen())
              .close(latestDaily.getClose())
              .high(latestQuarterly.getHigh())
              .low(latestQuarterly.getLow())
              .volume(latestQuarterly.getVolume().add(latestDaily.getVolume()))
              .build();
            if (latestDaily.getHigh() > latestQuarterly.getHigh()) {
                latestKLine.setHigh(latestDaily.getHigh());
            }
            if (latestDaily.getLow() < latestQuarterly.getLow()) {
                latestKLine.setLow(latestDaily.getLow());
            }
            return latestKLine;
        }
    }

    // 实时年k线
    public StockKLine realtimeYearKLine(StockKLine latestYearly, StockKLine latestDaily) {
        String yearlyDate = latestYearly.getDate();
        String dailyDate = latestDaily.getDate();

        LocalDate yearlyParse = LocalDate.parse(yearlyDate, DateTimeFormatter.ofPattern(DAY_FORMATTER));
        LocalDate dailyParse = LocalDate.parse(dailyDate, DateTimeFormatter.ofPattern(DAY_FORMATTER));

        int latestYear = yearlyParse.getYear();
        int dailyYear = dailyParse.getYear();

        // 如果最新年和最新日所在年不一样，则说明是新的一年
        if (dailyYear != latestYear) {
            String[] split = dailyDate.split("/");
            latestDaily.setDate(String.format("%s/%s/%s", split[0], "01", split[2]));
            return latestDaily;
        } else {
            StockKLine latestKLine = StockKLine.builder()
              .date(latestYearly.getDate())
              .open(latestYearly.getOpen())
              .close(latestDaily.getClose())
              .high(latestYearly.getHigh())
              .low(latestYearly.getLow())
              .volume(latestYearly.getVolume().add(latestDaily.getVolume()))
              .build();
            if (latestDaily.getHigh() > latestYearly.getHigh()) {
                latestKLine.setHigh(latestDaily.getHigh());
            }
            if (latestDaily.getLow() < latestYearly.getLow()) {
                latestKLine.setLow(latestDaily.getLow());
            }
            return latestKLine;
        }
    }

    public static void main(String[] args) {
        KLine kLine = new KLine();
        //        List<StockKLine> weekKLine = kLine.historicalWeekKLine(StockDailyTest.getDailyData());
        //        System.out.println(weekKLine);

        test_historicalYearKLine(kLine);
        //        test_realtimeWeekKLine(kLine);
        //        test_realtimeMonthKLine(kLine);
        //        test_realtimeQuarterKLine(kLine);
        //        test_realtimeYearKLine(kLine);
    }

    private static void test_historicalYearKLine(KLine kLine) {
        List<StockKLine> yearKLine = kLine.historicalYearKLine(StockDailyTest.getMonthlyData());
        System.out.println(yearKLine);
    }

    private static void test_realtimeWeekKLine(KLine kLine) {
        StockKLine weekly1 = StockKLine.builder().date("01/03/2022").open(133.88).close(133.41).high(134.26).low(129.44).volume(BigDecimal.valueOf(71379600)).build();
        StockKLine daily1 = StockKLine.builder().date("01/07/2022").open(131.25).close(133.49).high(133.51).low(130.46).volume(BigDecimal.valueOf(6945890)).build();
        StockKLine daily1_1 = StockKLine.builder().date("01/11/2022").open(131.25).close(133.49).high(133.51).low(130.46).volume(BigDecimal.valueOf(6945890)).build();
        System.out.println(kLine.realtimeWeekKLine(weekly1, daily1)); // old week
        System.out.println(kLine.realtimeWeekKLine(weekly1, daily1_1)); // new week

        StockKLine weekly2 = StockKLine.builder().date("12/30/2019").open(133.88).close(133.41).high(134.26).low(131.44).volume(BigDecimal.valueOf(71379600)).build();
        StockKLine daily2 = StockKLine.builder().date("01/03/2020").open(131.25).close(133.49).high(133.51).low(130.46).volume(BigDecimal.valueOf(6945890)).build();
        StockKLine daily2_2 = StockKLine.builder().date("01/07/2020").open(131.25).close(133.49).high(133.51).low(130.46).volume(BigDecimal.valueOf(6945890)).build();
        System.out.println(kLine.realtimeWeekKLine(weekly2, daily2)); // old week
        System.out.println(kLine.realtimeWeekKLine(weekly2, daily2_2)); // new week
    }

    private static void test_realtimeMonthKLine(KLine kLine) {
        StockKLine monthly1 = StockKLine.builder().date("04/01/2022").open(133.88).close(133.41).high(134.26).low(129.44).volume(BigDecimal.valueOf(71379600)).build();
        StockKLine daily1 = StockKLine.builder().date("04/27/2022").open(131.25).close(133.49).high(133.51).low(130.46).volume(BigDecimal.valueOf(6945890)).build();
        StockKLine daily1_1 = StockKLine.builder().date("05/02/2022").open(131.25).close(133.49).high(133.51).low(130.46).volume(BigDecimal.valueOf(6945890)).build();
        System.out.println(kLine.realtimeMonthKLine(monthly1, daily1)); // old month
        System.out.println(kLine.realtimeMonthKLine(monthly1, daily1_1)); // new month

        StockKLine monthly2 = StockKLine.builder().date("12/01/2022").open(133.88).close(133.41).high(134.26).low(129.44).volume(BigDecimal.valueOf(71379600)).build();
        StockKLine daily2 = StockKLine.builder().date("01/02/2023").open(131.25).close(133.49).high(133.51).low(130.46).volume(BigDecimal.valueOf(6945890)).build();
        System.out.println(kLine.realtimeMonthKLine(monthly2, daily2)); // new month
    }

    private static void test_realtimeQuarterKLine(KLine kLine) {
        StockKLine quarterly1 = StockKLine.builder().date("01/01/2022").open(133.88).close(133.41).high(134.26).low(129.44).volume(BigDecimal.valueOf(71379600)).build();
        StockKLine daily1 = StockKLine.builder().date("03/27/2022").open(131.25).close(133.49).high(133.51).low(130.46).volume(BigDecimal.valueOf(6945890)).build();
        StockKLine daily1_1 = StockKLine.builder().date("05/02/2022").open(131.25).close(133.49).high(133.51).low(130.46).volume(BigDecimal.valueOf(6945890)).build();
        System.out.println(kLine.realtimeQuarterKLine(quarterly1, daily1)); // old quarter
        System.out.println(kLine.realtimeQuarterKLine(quarterly1, daily1_1)); // new quarter

        StockKLine quarterly2 = StockKLine.builder().date("12/01/2022").open(133.88).close(133.41).high(134.26).low(129.44).volume(BigDecimal.valueOf(71379600)).build();
        StockKLine daily2 = StockKLine.builder().date("12/30/2022").open(131.25).close(133.49).high(133.51).low(130.46).volume(BigDecimal.valueOf(6945890)).build();
        StockKLine daily2_2 = StockKLine.builder().date("01/02/2023").open(131.25).close(133.49).high(133.51).low(130.46).volume(BigDecimal.valueOf(6945890)).build();
        System.out.println(kLine.realtimeQuarterKLine(quarterly2, daily2)); // old quarter
        System.out.println(kLine.realtimeQuarterKLine(quarterly2, daily2_2)); // new quarter
    }

    private static void test_realtimeYearKLine(KLine kLine) {
        StockKLine yearly1 = StockKLine.builder().date("01/01/2022").open(133.88).close(133.41).high(134.26).low(129.44).volume(BigDecimal.valueOf(71379600)).build();
        StockKLine daily1 = StockKLine.builder().date("03/27/2022").open(131.25).close(133.49).high(133.51).low(130.46).volume(BigDecimal.valueOf(6945890)).build();
        StockKLine daily1_1 = StockKLine.builder().date("01/02/2023").open(131.25).close(133.49).high(133.51).low(130.46).volume(BigDecimal.valueOf(6945890)).build();
        System.out.println(kLine.realtimeYearKLine(yearly1, daily1)); // old year
        System.out.println(kLine.realtimeYearKLine(yearly1, daily1_1)); // new year
    }
}
