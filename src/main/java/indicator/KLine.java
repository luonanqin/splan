package indicator;

import bean.StockKLine;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Created by Luonanqin on 2023/2/1.
 */
public class KLine {

    // 历史周k线
    public List<StockKLine> historicalWeekKLine(List<StockKLine> dailyList) {
        List<StockKLine> week = Lists.newArrayList();

        //        boolean startNewWeek = false;
        String weekDate = null;
        double weekOpen = 0, weekClose = 0, weekHigh = 0, weekLow = 1000000;
        BigDecimal weekVolumn = BigDecimal.ZERO;

        for (int i = dailyList.size() - 1; i >= 0; i--) {
            StockKLine daily = dailyList.get(i);
            String date = daily.getDate();
            double open = daily.getOpen();
            double close = daily.getClose();
            double high = daily.getHigh();
            double low = daily.getLow();
            BigDecimal volumn = daily.getVolumn();

            LocalDate localDate = LocalDate.parse(date, DateTimeFormatter.ofPattern("MM/dd/yyyy"));
            int dayOfWeek = localDate.getDayOfWeek().getValue();

            // 后一天是周一 或者 后一天和前一天之间有没有周末
            if (dayOfWeek == 1 && StringUtils.isNotBlank(weekDate)) {
                StockKLine kLine = StockKLine.builder().date(weekDate).open(weekOpen).close(weekClose).high(weekHigh).low(weekLow).volumn(weekVolumn).build();
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
                weekVolumn = weekVolumn.add(volumn);
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
            BigDecimal volumn = monthly.getVolumn();

            LocalDate localDate = LocalDate.parse(date, DateTimeFormatter.ofPattern("MM/dd/yyyy"));
            int year = localDate.getYear();
            int month = localDate.getMonthValue();

            if (month == 12 && StringUtils.isNotBlank(yearDate)) {
                StockKLine kLine = StockKLine.builder().date(yearDate).open(yearOpen).close(yearClose).high(yearHigh).low(yearLow).volumn(yearVolumn).build();
                yearList.add(kLine);

                yearOpen = 0;
                yearClose = 0;
                yearHigh = 0;
                yearLow = 1000000;
                yearVolumn = BigDecimal.ZERO;
            }

            if (yearClose == 0) {
                yearClose = close;
                yearDate = String.valueOf(year);
            }
            if (high > yearHigh) {
                yearHigh = high;
            }
            if (low < yearLow) {
                yearLow = low;
            }
            yearOpen = open;
            yearVolumn = yearVolumn.add(volumn);

            if (i + 1 == monthlyList.size()) {
                StockKLine kLine = StockKLine.builder().date(yearDate).open(yearOpen).close(yearClose).high(yearHigh).low(yearLow).volumn(yearVolumn).build();
                yearList.add(kLine);
            }
        }
        return yearList;
    }

    // 实时周k线
    public StockKLine realtimeWeekKLine(StockKLine latestWeekly, StockKLine latestDaily) {
        String weeklyDate = latestWeekly.getDate();
        String dailyDate = latestDaily.getDate();

        LocalDate weeklyParse = LocalDate.parse(weeklyDate, DateTimeFormatter.ofPattern("MM/dd/yyyy"));
        LocalDate dailyParse = LocalDate.parse(dailyDate, DateTimeFormatter.ofPattern("MM/dd/yyyy"));

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
                latestDaily.setDate(dailyParse.format(DateTimeFormatter.ofPattern("MM/dd/yyyy")));
            }
            return latestDaily;
        } else {
            StockKLine latestKLine = StockKLine.builder()
              .date(latestWeekly.getDate())
              .open(latestWeekly.getOpen())
              .close(latestDaily.getClose())
              .high(latestWeekly.getHigh())
              .low(latestWeekly.getLow())
              .volumn(latestWeekly.getVolumn().add(latestDaily.getVolumn()))
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
    public List<StockKLine> realtimeMonthKLine(List<StockKLine> dailyList) {
        return null;
    }

    // 实时季k线
    public List<StockKLine> realtimeSeasonKLine(List<StockKLine> dailyList) {
        return null;
    }

    // 实时年k线
    public List<StockKLine> realtimeYearKLine(List<StockKLine> dailyList) {
        return null;
    }

    public static void main(String[] args) {
        KLine kLine = new KLine();
        //        List<StockKLine> weekKLine = kLine.historicalWeekKLine(StockDailyTest.getDailyData());
        //        System.out.println(weekKLine);

        //        List<StockKLine> yearKLine = kLine.historicalYearKLine(StockDailyTest.getMonthlyData());
        //        System.out.println(yearKLine);

        StockKLine weekly1 = StockKLine.builder().date("01/03/2022").open(133.88).close(133.41).high(134.26).low(129.44).volumn(BigDecimal.valueOf(71379600)).build();
        StockKLine daily1 = StockKLine.builder().date("01/07/2022").open(131.25).close(133.49).high(133.51).low(130.46).volumn(BigDecimal.valueOf(6945890)).build();
        StockKLine daily1_1 = StockKLine.builder().date("01/11/2022").open(131.25).close(133.49).high(133.51).low(130.46).volumn(BigDecimal.valueOf(6945890)).build();
        System.out.println(kLine.realtimeWeekKLine(weekly1, daily1)); // old week
        System.out.println(kLine.realtimeWeekKLine(weekly1, daily1_1)); // new week

        StockKLine weekly2 = StockKLine.builder().date("12/30/2019").open(133.88).close(133.41).high(134.26).low(131.44).volumn(BigDecimal.valueOf(71379600)).build();
        StockKLine daily2 = StockKLine.builder().date("01/03/2020").open(131.25).close(133.49).high(133.51).low(130.46).volumn(BigDecimal.valueOf(6945890)).build();
        StockKLine daily2_2 = StockKLine.builder().date("01/07/2020").open(131.25).close(133.49).high(133.51).low(130.46).volumn(BigDecimal.valueOf(6945890)).build();
        System.out.println(kLine.realtimeWeekKLine(weekly2, daily2)); // old week
        System.out.println(kLine.realtimeWeekKLine(weekly2, daily2_2)); // new week
    }
}
