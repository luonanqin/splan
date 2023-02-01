package indicator;

import bean.StockDaily;
import bean.StockKLine;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import test.StockDailyTest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Created by Luonanqin on 2023/2/1.
 */
public class KLine {

    // 历史周k线
    public List<StockKLine> historicalWeekKLine(List<StockDaily> dailyList) {
        List<StockKLine> week = Lists.newArrayList();

        //        boolean startNewWeek = false;
        String weekDate = null;
        double weekOpen = 0, weekClose = 0, weekHigh = 0, weekLow = 1000000;
        BigDecimal weekVolumn = BigDecimal.ZERO;

        for (int i = dailyList.size() - 1; i >= 0; i--) {
            StockDaily daily = dailyList.get(i);
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

    // 历史月k线
    public List<StockKLine> historicalMonthKLine(List<StockDaily> dailyList) {
        return null;
    }

    // 历史季k线
    public List<StockKLine> historicalSeasonKLine(List<StockDaily> dailyList) {
        return null;
    }

    // 历史年k线
    public List<StockKLine> historicalYearKLine(List<StockDaily> dailyList) {
        return null;
    }

    // 实时周k线
    public StockKLine realtimeWeekKLine(List<StockDaily> dailyList) {

        return null;
    }

    // 实时月k线
    public List<StockKLine> realtimeMonthKLine(List<StockDaily> dailyList) {
        return null;
    }

    // 实时季k线
    public List<StockKLine> realtimeSeasonKLine(List<StockDaily> dailyList) {
        return null;
    }

    // 实时年k线
    public List<StockKLine> realtimeYearKLine(List<StockDaily> dailyList) {
        return null;
    }

    public static void main(String[] args) {
        KLine kLine = new KLine();
        List<StockKLine> weekKLine = kLine.historicalWeekKLine(StockDailyTest.getDailyData());
        System.out.println(weekKLine);
    }
}
