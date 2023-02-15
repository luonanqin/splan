package stock;

import bean.StockKLine;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import util.BaseUtils;

import java.io.File;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static util.Constants.FIX_WEEKLY_PATH;
import static util.Constants.FORMATTER;

/**
 * Created by Luonanqin on 2023/2/13.
 */
public class FixDownloadData {

    public static void main(String[] args) throws Exception {
        // 加载weekly下的文件列表，转换成stock列表大写
        Map<String, String> weeklyMap = BaseUtils.originStockFileMap("weekly");
        Map<String, String> dailyMap = BaseUtils.originStockFileMap("daily");
        // 加载fixWeekly下的文件列表，转换成stock列表大写
        Set<String> fixedWeeklySet = fixedWeeklyList();
        // 只有weekly中有且fixWeekly没有的数据才需要fix
        for (String stock : weeklyMap.keySet()) {
            if (fixedWeeklySet.contains(stock)) {
                System.out.println("has fixed: " + stock);
                continue;
            }

            // 加载weekly数据
            String weeklyFile = weeklyMap.get(stock);
            List<StockKLine> originWeeklyData = BaseUtils.loadOriginalData(weeklyFile);

            // 加载daily数据
            String dailyFile = dailyMap.get(stock);
            List<StockKLine> originDailyData = BaseUtils.loadOriginalData(dailyFile);

            int weekIdx = 0;
            LocalDate weekDate = LocalDate.now();
            BigDecimal sum = BigDecimal.ZERO;
            boolean firstTime = true;
            StockKLine weekK = null;
            List<StockKLine> newWeekList = Lists.newArrayList();
            int dayCount = 0;
            for (StockKLine dayK : originDailyData) {
                LocalDate dayDate = LocalDate.parse(dayK.getDate(), FORMATTER);

                while (weekDate.isAfter(dayDate)) {
                    if (!firstTime) {
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
                        if (!weekK.getVolume().multiply(BigDecimal.valueOf(dayCount)).equals(sum)) {
                            System.out.println(weekK);
                        }

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
                if (weekIdx >= originWeeklyData.size()) {
                    break;
                }
                if (firstTime) {
                    firstTime = false;
                }

                sum = sum.add(dayK.getVolume());
                dayCount++;
            }
            BaseUtils.writeStockKLine(FIX_WEEKLY_PATH + stock, newWeekList);
            System.out.println("fix finish: " + stock);
        }
        // 当daily某天小于weekly的某天时，开始累加周成交量x，
        // 当daily某天小于下一个weekly的某天时，最新一周成交量累加结束，结果加入集合，并清零x，接着继续累加新的成交量
        // 结束

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
