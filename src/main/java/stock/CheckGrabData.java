package stock;

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
public class CheckGrabData {


    private static Set<String> hasMergeStock = Sets.newHashSet();
    private static Map<String, String> originWeeklyMap = Maps.newHashMap();
    private static Map<String, String> originDailyMap = Maps.newHashMap();
    private static Map<String, String> originMonthlyMap = Maps.newHashMap();
    private static Map<String, String> grabDailyMap = Maps.newHashMap();

    public static void main(String[] args) throws Exception {
        // 加载标准数据目录下的stock列表
        hasMergeStock = BaseUtils.getFileMap(STD_DAILY_PATH).keySet().stream().map(f -> f.toUpperCase()).collect(Collectors.toSet());

        // 加载原始数据文件列表，转换成stock列表大写
        originWeeklyMap = BaseUtils.originStockFileMap("weekly");
        originDailyMap = BaseUtils.originStockFileMap("daily");
        originMonthlyMap = BaseUtils.originStockFileMap("monthly");

        // 加载抓取数据文件列表，转换成stock列表大写
        grabDailyMap = BaseUtils.grabStockFileMap();
        // 加载fixWeekly下的文件列表，转换成stock列表大写
        //        Set<String> fixedWeeklySet = fixedWeeklyList();
        Set<String> fixedWeeklySet = Sets.newHashSet();
        // 只有weekly中有且fixWeekly没有的数据才需要fix
        fixData(fixedWeeklySet);
    }

    private static void fixData(Set<String> fixedWeeklySet) throws Exception {
        for (String stock : originWeeklyMap.keySet()) {
            if (fixedWeeklySet.contains(stock)) {
                System.out.println("has fixed: " + stock);
                continue;
            }
            if (hasMergeStock.contains(stock)) {
                continue;
            }
            if (!stock.equals("AAPL")) {
                //                continue;
            }

            // 加载weekly数据
            String weeklyFile = originWeeklyMap.get(stock);
            List<StockKLine> originWeeklyData = BaseUtils.loadOriginalData(weeklyFile);

            // 合并原始和补抓daily数据
            List<StockKLine> dailyData = Lists.newArrayList();

            // 加载origin daily数据
            String originDailyFile = originDailyMap.get(stock);
            if (StringUtils.isBlank(originDailyFile)) {
                System.out.println("empty origin file: " + stock);
                continue;
            }
            List<StockKLine> originDailyData = BaseUtils.loadOriginalData(originDailyFile);
            dailyData.addAll(originDailyData);

            // 加载grab daily数据
            String dailyFile = grabDailyMap.get(stock);
            if (StringUtils.isNotBlank(dailyFile)) {
                List<StockKLine> grabDailyData = BaseUtils.loadOriginalData(dailyFile);
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
            System.out.println("fix finish: " + stock);
        }
    }

    private static boolean checkSum(String stock, BigDecimal sum, StockKLine weekK, int dayCount) {
        BigDecimal count = BigDecimal.valueOf(dayCount);
        BigDecimal multiply = weekK.getVolume().multiply(count).setScale(0);
        sum = sum.setScale(0);
        BigDecimal divide = null;
        try {
            divide = sum.divide(count, 0, BigDecimal.ROUND_DOWN).setScale(0);
        } catch (Exception e) {
            //            System.out.println(stock + " " + weekK.getDate() + " week multi: " + multiply + " week: " + weekK.getVolume() + " sum: " + sum + " dayCount: " + dayCount);
            System.out.println(stock + " " + weekK.getDate());
            return false;
        }
        if (!(multiply.equals(sum) || divide.equals(weekK.getVolume().setScale(0)))) {
            //            System.out.println(stock + " " + weekK.getDate() + " week multi: " + multiply + " week: " + weekK.getVolume() + " sum: " + sum + " dayCount: " + dayCount);
            System.out.println(stock + " " + weekK.getDate());
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
