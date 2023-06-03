package luonq.stock;

import bean.StockKLine;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.lang3.StringUtils;
import util.BaseUtils;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static util.Constants.*;

/**
 * Created by Luonanqin on 2023/2/13.
 */
public class CheckAndFixData {


    private static Map<String, String> originWeeklyMap = Maps.newHashMap();
    private static Map<String, String> originDailyMap = Maps.newHashMap();
    private static Map<String, String> grabDailyMap = Maps.newHashMap();

    public static void main(String[] args) throws Exception {

        // 加载原始数据文件列表，转换成stock列表大写
        originWeeklyMap = BaseUtils.originStockFileMap("weekly");
        originDailyMap = BaseUtils.originStockFileMap("daily");

        // 加载抓取数据文件列表，转换成stock列表大写
        grabDailyMap = BaseUtils.grabStockFileMap();

        computeDaily();
        fixDailyAndWeekly();
        fixMonthly();
        computeWeekly();
        computeMonthly();
        computeQuarterly();
        computeYearly();
    }

    // 只计算2000年后的daily
    private static void computeDaily() throws Exception {
        Set<String> hasStdStock = BaseUtils.getFileMap(STD_DAILY_PATH).keySet().stream().map(f -> f.toUpperCase()).collect(Collectors.toSet());

        for (String stock : originDailyMap.keySet()) {
            if (hasStdStock.contains(stock)) {
                continue;
            }

            String dailyFile = originDailyMap.get(stock);
            if (!BaseUtils.after_2000(dailyFile)) {
                continue;
            }

            List<StockKLine> dailyData = BaseUtils.loadDataToKline(dailyFile);
            BaseUtils.writeStockKLine(STD_DAILY_PATH + stock, dailyData);
            System.out.println("conpute day finish: " + stock);
        }

    }

    // 每次只能检查出一个错误，重抓并且merge之后再运行检查还有没有错误
    private static void fixDailyAndWeekly() throws Exception {
        Set<String> hasMergeStock = BaseUtils.getFileMap(STD_DAILY_PATH).keySet().stream().map(f -> f.toUpperCase()).collect(Collectors.toSet());

        for (String stock : originWeeklyMap.keySet()) {
            if (hasMergeStock.contains(stock)) {
                continue;
            }
            if (!stock.equals("DINO")) {
//                                                continue;
            }

            // 加载weekly数据
            String weeklyFile = originWeeklyMap.get(stock);
            List<StockKLine> originWeeklyData = BaseUtils.loadDataToKline(weeklyFile);

            // 合并原始和补抓daily数据
            List<StockKLine> dailyData = Lists.newArrayList();

            // 加载origin daily数据
            String originDailyFile = originDailyMap.get(stock);
            if (StringUtils.isBlank(originDailyFile)) {
                //                System.out.println("empty origin file: " + stock);
                continue;
            }
            List<StockKLine> originDailyData = BaseUtils.loadDataToKline(originDailyFile);
            dailyData.addAll(originDailyData);

            if (!BaseUtils.after_2000(originDailyFile)) {
                // 加载grab daily数据
                String dailyFile = grabDailyMap.get(stock);
                if (StringUtils.isBlank(dailyFile)) {
                    System.out.println("has not grab: " + stock);
                    continue;
                }
                List<StockKLine> grabDailyData = BaseUtils.loadDataToKline(dailyFile);
                dailyData.addAll(grabDailyData);
            }

            int weekIdx = 0;
            LocalDate weekDate = LocalDate.now();
            BigDecimal sum = BigDecimal.ZERO;
            boolean firstTime = true;
            StockKLine weekK = null;
            List<StockKLine> newWeekList = Lists.newArrayList();
            int dayCount = 0;
            boolean checkSumSuccess = true;
            for (StockKLine dayK : dailyData) {
                LocalDate dayDate = LocalDate.parse(dayK.getDate(), FORMATTER);

                while (weekDate.isAfter(dayDate)) {
                    if (!firstTime) {
                        // 当daily某天小于下一个weekly的某天时，最新一周成交量累加结束，结果加入集合，并清零x，接着继续累加新的成交量
                        StockKLine newWeek = StockKLine.builder()
                          .date(weekK.getDate())
                          .open(weekK.getOpen())
                          .close(weekK.getClose())
                          .high(weekK.getHigh())
                          .low(weekK.getLow())
                          .change(weekK.getChange())
                          .changePnt(weekK.getChangePnt())
                          .volume(sum)
                          .build();
                        newWeekList.add(newWeek);

                        checkSumSuccess = checkSum(stock, sum, weekK, dayCount);

                        sum = BigDecimal.ZERO;
                        dayCount = 0;
                    }
                    weekK = originWeeklyData.get(weekIdx);
                    weekDate = LocalDate.parse(weekK.getDate(), FORMATTER);
                    weekIdx++;
                    if (weekIdx >= originWeeklyData.size()) {
                        break;
                    }
                }
                if (!checkSumSuccess) {
                    break;
                }
                if (weekIdx >= originWeeklyData.size()) {
                    break;
                }
                if (firstTime) {
                    firstTime = false;
                }

                // 当daily某天小于weekly的某天时，开始累加周成交量x，
                sum = sum.add(dayK.getVolume());
                dayCount++;
            }
            if (!checkSumSuccess) {
                //                System.out.println("check sum failed: " + stock);
                continue;
            }
            BaseUtils.writeStockKLine(STD_WEEKLY_PATH + stock, newWeekList);
            BaseUtils.writeStockKLine(STD_DAILY_PATH + stock, dailyData);
            System.out.println("fix week finish: " + stock);
        }
    }

    private static void fixMonthly() throws Exception {
        Set<String> hasMergeStock = BaseUtils.getFileMap(STD_MONTHLY_PATH).keySet().stream().map(f -> f.toUpperCase()).collect(Collectors.toSet());

        Map<String, String> originMonthlyMap = BaseUtils.originStockFileMap("monthly");
        for (String stock : originMonthlyMap.keySet()) {
            if (hasMergeStock.contains(stock)) {
                continue;
            }
            if (!stock.equals("AAPL")) {
                //                                continue;
            }

            // 加载monthly数据
            String monthlyFile = originMonthlyMap.get(stock);
            List<StockKLine> originMonthlyData = BaseUtils.loadDataToKline(monthlyFile);

            // 合并原始和补抓daily数据
            List<StockKLine> dailyData = Lists.newArrayList();

            // 加载origin daily数据
            String originDailyFile = originDailyMap.get(stock);
            if (StringUtils.isBlank(originDailyFile)) {
                //                System.out.println("empty origin file: " + stock);
                continue;
            }
            List<StockKLine> originDailyData = BaseUtils.loadDataToKline(originDailyFile);
            dailyData.addAll(originDailyData);

            if (!BaseUtils.after_2000(originDailyFile)) {
                // 加载grab daily数据
                String dailyFile = grabDailyMap.get(stock);
                if (StringUtils.isBlank(dailyFile)) {
                    continue;
                }
                List<StockKLine> grabDailyData = BaseUtils.loadDataToKline(dailyFile);
                dailyData.addAll(grabDailyData);
            }

            int monthIdx = 0;
            LocalDate monthDate = LocalDate.now();
            BigDecimal sum = BigDecimal.ZERO;
            boolean firstTime = true;
            StockKLine monthK = null;
            List<StockKLine> newMonthList = Lists.newArrayList();
            int dayCount = 0;
            boolean checkSumSuccess = true;
            boolean outIndex = false;
            for (StockKLine dayK : dailyData) {
                LocalDate dayDate = LocalDate.parse(dayK.getDate(), FORMATTER);

                while (monthDate.isAfter(dayDate)) {
                    if (!firstTime) {
                        // 当daily某天小于下一个monthly的某天时，最新一周成交量累加结束，结果加入集合，并清零x，接着继续累加新的成交量
                        StockKLine newMonth = StockKLine.builder()
                          .date(monthK.getDate())
                          .open(monthK.getOpen())
                          .close(monthK.getClose())
                          .high(monthK.getHigh())
                          .low(monthK.getLow())
                          .change(monthK.getChange())
                          .changePnt(monthK.getChangePnt())
                          .volume(sum)
                          .build();
                        newMonthList.add(newMonth);

                        checkSumSuccess = checkSum(stock, sum, monthK, dayCount);

                        sum = BigDecimal.ZERO;
                        dayCount = 0;
                    }
                    if (monthIdx >= originMonthlyData.size()) {
                        outIndex = true;
                        break;
                    }
                    monthK = originMonthlyData.get(monthIdx);
                    monthDate = LocalDate.parse(monthK.getDate(), FORMATTER);
                    monthIdx++;
                }
                if (!checkSumSuccess) {
                    break;
                }
                if (outIndex) {
                    break;
                }
                if (firstTime) {
                    firstTime = false;
                }

                // 当daily某天小于monthly的某天时，开始累加周成交量x，
                sum = sum.add(dayK.getVolume());
                dayCount++;
            }
            if (dayCount != 0) {
                StockKLine newMonth = StockKLine.builder()
                  .date(monthK.getDate())
                  .open(monthK.getOpen())
                  .close(monthK.getClose())
                  .high(monthK.getHigh())
                  .low(monthK.getLow())
                  .change(monthK.getChange())
                  .changePnt(monthK.getChangePnt())
                  .volume(sum)
                  .build();
                newMonthList.add(newMonth);
            }
            if (!checkSumSuccess) {
                //                System.out.println("check sum failed: " + stock);
                continue;
            }
            BaseUtils.writeStockKLine(STD_MONTHLY_PATH + stock, newMonthList);
            System.out.println("fix month finish: " + stock);
        }
    }

    public static void computeWeekly() throws Exception {
        Set<String> hasMergeWeek = BaseUtils.getFileMap(STD_WEEKLY_PATH).keySet().stream().map(f -> f.toUpperCase()).collect(Collectors.toSet());

        for (String stock : originDailyMap.keySet()) {
            if (hasMergeWeek.contains(stock)) {
                continue;
            }

            String dailyFile = originDailyMap.get(stock);
            if (!BaseUtils.after_2000(dailyFile)) {
                continue;
            }

            if (!stock.equals("AWK")) {
                //                continue;
            }

            List<StockKLine> dailyData = BaseUtils.loadDataToKline(dailyFile);

            List<StockKLine> weeekList = Lists.newArrayList();
            double low = Double.MAX_VALUE, high = Double.MIN_VALUE, open = 0, close = 0;
            BigDecimal volumn = BigDecimal.ZERO;
            boolean first = true;
            String firstWeekDay = null;
            int size = dailyData.size();
            int i = 0;
            LocalDate init = LocalDate.parse("12/30/2022", FORMATTER);
            while (i < size) {
                StockKLine dailyK = dailyData.get(i);
                String date = dailyK.getDate();
                String initDate = init.format(FORMATTER);
                int dayOfWeek = init.getDayOfWeek().getValue();

                if (dayOfWeek == 1) {
                    firstWeekDay = initDate;
                }

                init = init.minusDays(1);
                if (date.equals(initDate)) {
                    i++;
                } else {
                    if (dayOfWeek == 7) {
                        StockKLine week = StockKLine.builder()
                          .date(firstWeekDay)
                          .open(open)
                          .close(close)
                          .high(high)
                          .low(low)
                          .change(0)
                          .changePnt(0)
                          .volume(volumn)
                          .build();

                        weeekList.add(week);

                        low = Double.MAX_VALUE;
                        high = Double.MIN_VALUE;
                        close = 0;
                        volumn = BigDecimal.ZERO;

                        first = true;
                    }
                    continue;
                }

                if (first) {
                    close = dailyK.getClose();
                    first = false;
                }

                open = dailyK.getOpen();
                if (dailyK.getLow() < low) {
                    low = dailyK.getLow();
                }
                if (dailyK.getHigh() > high) {
                    high = dailyK.getHigh();
                }
                volumn = volumn.add(dailyK.getVolume());
            }
            BaseUtils.writeStockKLine(STD_WEEKLY_PATH + stock, weeekList);
            System.out.println("compute week finish: " + stock);
        }

    }

    public static void computeMonthly() throws Exception {
        Set<String> hasMergeMonth = BaseUtils.getFileMap(STD_MONTHLY_PATH).keySet().stream().map(f -> f.toUpperCase()).collect(Collectors.toSet());
        Map<String, String> stdDailyMap = BaseUtils.getFileMap(STD_DAILY_PATH);

        for (String stock : stdDailyMap.keySet()) {
            if (!stock.equals("SO")) {
//                continue;
            }

            if (hasMergeMonth.contains(stock)) {
                continue;
            }

            String dailyFile = stdDailyMap.get(stock);
//            if (!BaseUtils.after_2000(dailyFile)) {
//                continue;
//            }
            StockKLine firstKLine = BaseUtils.getFirstKLine(dailyFile);
            if (firstKLine.getOpen()<1) {
                System.out.println(stock);
                continue;
            }

            List<StockKLine> dailyData = BaseUtils.loadDataToKline(dailyFile);

            List<StockKLine> monthList = Lists.newArrayList();
            double low = Double.MAX_VALUE, high = Double.MIN_VALUE, open = 0, close = 0;
            BigDecimal volumn = BigDecimal.ZERO;
            boolean first = true;
            String firstMonthDay = null;
            int size = dailyData.size();
            int i = 0;
            LocalDate init = LocalDate.parse("12/30/2022", FORMATTER);
            while (i < size) {
                StockKLine dailyK = dailyData.get(i);
                String date = dailyK.getDate();
                String initDate = init.format(FORMATTER);
                int dayOfMonth = init.getDayOfMonth();
                int monthValue = init.getMonthValue();

                if (dayOfMonth == 1) {
                    firstMonthDay = initDate;
                }

                init = init.minusDays(1);
                if (date.equals(initDate)) {
                    i++;

                    if (first) {
                        close = dailyK.getClose();
                        first = false;
                    }

                    open = dailyK.getOpen();
                    if (dailyK.getLow() < low) {
                        low = dailyK.getLow();
                    }
                    if (dailyK.getHigh() > high) {
                        high = dailyK.getHigh();
                    }
                    volumn = volumn.add(dailyK.getVolume());
                }

                int nextMonthValue = init.getMonthValue();
                if (nextMonthValue != monthValue) {
                    StockKLine month = StockKLine.builder()
                      .date(initDate)
                      .open(open)
                      .close(close)
                      .high(high)
                      .low(low)
                      .change(0)
                      .changePnt(0)
                      .volume(volumn)
                      .build();

                    monthList.add(month);

                    low = Double.MAX_VALUE;
                    high = Double.MIN_VALUE;
                    close = 0;
                    volumn = BigDecimal.ZERO;

                    first = true;
                }
            }
            BaseUtils.writeStockKLine(STD_MONTHLY_PATH + stock, monthList);
            System.out.println("compute month finish: " + stock);
        }
    }


    public static void computeQuarterly() throws Exception {
        Set<String> hasMergeStock = BaseUtils.getFileMap(STD_QUARTERLY_PATH).keySet().stream().map(f -> f.toUpperCase()).collect(Collectors.toSet());

        Map<String, String> stdMonthlyMap = BaseUtils.getFileMap(STD_MONTHLY_PATH);
        for (String stock : stdMonthlyMap.keySet()) {
            if (hasMergeStock.contains(stock)) {
                continue;
            }
            if (!stock.equals("AAPL")) {
                //                continue;
            }

            // 加载monthly数据
            String monthlyFile = stdMonthlyMap.get(stock);
            List<StockKLine> stdMonthlyData = BaseUtils.loadDataToKline(monthlyFile);

            List<StockKLine> quarterList = Lists.newArrayList();
            double low = Double.MAX_VALUE, high = Double.MIN_VALUE, open, close = 0;
            BigDecimal volumn = BigDecimal.ZERO;
            for (int i = 0; i < stdMonthlyData.size(); i++) {
                StockKLine monthK = stdMonthlyData.get(i);

                if ((i + 3) % 3 == 0) {
                    close = monthK.getClose();
                }
                if (monthK.getLow() < low) {
                    low = monthK.getLow();
                }
                if (monthK.getHigh() > high) {
                    high = monthK.getHigh();
                }
                volumn = volumn.add(monthK.getVolume());

                if ((i + 1) % 3 == 0 || i == stdMonthlyData.size() - 1) {
                    open = monthK.getOpen();

                    StockKLine quarter = StockKLine.builder()
                      .date(monthK.getDate())
                      .open(open)
                      .close(close)
                      .high(high)
                      .low(low)
                      .change(0)
                      .changePnt(0)
                      .volume(volumn)
                      .build();

                    quarterList.add(quarter);

                    low = Double.MAX_VALUE;
                    high = Double.MIN_VALUE;
                    close = 0;
                    volumn = BigDecimal.ZERO;
                }
            }

            BaseUtils.writeStockKLine(STD_QUARTERLY_PATH + stock, quarterList);
            System.out.println("fix quarter finish: " + stock);
        }
    }

    public static void computeYearly() throws Exception {
        Set<String> hasMergeStock = BaseUtils.getFileMap(STD_YEARLY_PATH).keySet().stream().map(f -> f.toUpperCase()).collect(Collectors.toSet());

        Map<String, String> stdMonthlyMap = BaseUtils.getFileMap(STD_MONTHLY_PATH);
        for (String stock : stdMonthlyMap.keySet()) {
            if (hasMergeStock.contains(stock)) {
                continue;
            }
            if (!stock.equals("AAPL")) {
                //                continue;
            }

            // 加载monthly数据
            String monthlyFile = stdMonthlyMap.get(stock);
            List<StockKLine> stdMonthlyData = BaseUtils.loadDataToKline(monthlyFile);

            List<StockKLine> yearList = Lists.newArrayList();
            double low = Double.MAX_VALUE, high = Double.MIN_VALUE, open, close = 0;
            BigDecimal volumn = BigDecimal.ZERO;
            for (int i = 0; i < stdMonthlyData.size(); i++) {
                StockKLine monthK = stdMonthlyData.get(i);

                if ((i + 12) % 12 == 0) {
                    close = monthK.getClose();
                }
                if (monthK.getLow() < low) {
                    low = monthK.getLow();
                }
                if (monthK.getHigh() > high) {
                    high = monthK.getHigh();
                }
                volumn = volumn.add(monthK.getVolume());

                if ((i + 1) % 12 == 0 || i == stdMonthlyData.size() - 1) {
                    open = monthK.getOpen();

                    StockKLine year = StockKLine.builder()
                      .date(monthK.getDate())
                      .open(open)
                      .close(close)
                      .high(high)
                      .low(low)
                      .change(0)
                      .changePnt(0)
                      .volume(volumn)
                      .build();

                    yearList.add(year);

                    low = Double.MAX_VALUE;
                    high = Double.MIN_VALUE;
                    close = 0;
                    volumn = BigDecimal.ZERO;
                }
            }

            BaseUtils.writeStockKLine(STD_YEARLY_PATH + stock, yearList);
            System.out.println("fix year finish: " + stock);
        }
    }

    private static boolean checkSum(String stock, BigDecimal sum, StockKLine weekK, int dayCount) {
        BigDecimal count = BigDecimal.valueOf(dayCount);
        BigDecimal multiply = weekK.getVolume().multiply(count).setScale(0);
        sum = sum.setScale(0, BigDecimal.ROUND_DOWN);
        BigDecimal divide = null;
        try {
            divide = sum.divide(count, 0, BigDecimal.ROUND_DOWN).setScale(0);
        } catch (Exception e) {
                        System.out.println(stock + " " + weekK.getDate() + " week multi: " + multiply + " week: " + weekK.getVolume() + " sum: " + sum + " dayCount: " + dayCount);
//            System.out.println(stock + " " + weekK.getDate());
            return false;
        }
        if (!(multiply.equals(sum) || divide.equals(weekK.getVolume().setScale(0)))) {
                                    System.out.println(stock + " " + weekK.getDate() + " week multi: " + multiply + " week: " + weekK.getVolume() + " sum: " + sum + " dayCount: " + dayCount);
//            System.out.println(stock + " " + weekK.getDate());
            return false;
        }
        return true;
    }

    public static Set<String> fixedWeeklyList() throws Exception {
        Set<String> set = Sets.newHashSet();

        File dir = new File(FIX_WEEKLY_PATH);
        for (String file : dir.list()) {
            set.add(file.toUpperCase());
        }

        return set;
    }


}
