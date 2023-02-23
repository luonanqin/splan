package stock;

import bean.StockKLine;
import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import util.BaseUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static util.BaseUtils.convertToKLine;
import static util.BaseUtils.readFile;
import static util.Constants.*;

/**
 * Created by Luonanqin on 2023/2/13.
 */
public class FixMonthVolumn {

    public static void main(String[] args) throws Exception {
        // 加载原始monthly文件，转换成stock列表大写
        Map<String, String> monthlyFileMap = BaseUtils.originStockFileMap("monthly");
        // 加载标准daily文件
        Map<String, String> stdDailyMap = BaseUtils.getFileMap(STD_DAILY_PATH);

        fixData(monthlyFileMap, stdDailyMap);
    }

    private static void fixData(Map<String, String> monthlyFileMap, Map<String, String> stdDailyMap) throws Exception {
        for (String stock : monthlyFileMap.keySet()) {
            if (!stock.equals("AAPL")) {
                continue;
            }

            // 加载monthly数据
            String monthlyFile = monthlyFileMap.get(stock);
            List<String> monthList = readFile(monthlyFile);
            monthList.remove(0);
            monthList.remove(monthList.size() - 1);
            List<StockKLine> originMonthlyData = convertToKLine(monthList);

            // 加载daily数据
            String dailyFile = stdDailyMap.get(stock);
            if (StringUtils.isBlank(dailyFile)) {
                //                System.out.println("grab file isn't exist: " + stock);
                continue;
            }
            List<StockKLine> stdDailyData = convertToKLine(readFile(dailyFile));

            int monthIdx = 0;
            LocalDate monthDate = LocalDate.now();
            BigDecimal sum = BigDecimal.ZERO;
            boolean firstTime = true;
            StockKLine monthK = null;
            List<StockKLine> newMonthList = Lists.newArrayList();
            for (StockKLine dayK : stdDailyData) {
                LocalDate dayDate = LocalDate.parse(dayK.getDate(), FORMATTER);

                while (monthDate.isAfter(dayDate)) {
                    if (!firstTime) {
                        StockKLine newmonth = StockKLine.builder()
                          .date(monthK.getDate())
                          .open(monthK.getOpen())
                          .close(monthK.getClose())
                          .high(monthK.getHigh())
                          .low(monthK.getLow())
                          .change(monthK.getChange())
                          .changePnt(monthK.getChangePnt())
                          .volume(sum)
                          .build();
                        newMonthList.add(newmonth);

                        sum = BigDecimal.ZERO;
                    }
                    monthK = originMonthlyData.get(monthIdx);
                    monthDate = LocalDate.parse(monthK.getDate(), FORMATTER);
                    monthIdx++;
                    if (monthIdx >= originMonthlyData.size()) {
                        break;
                    }
                }
                if (monthIdx >= originMonthlyData.size()) {
                    break;
                }
                if (firstTime) {
                    firstTime = false;
                }

                // 当daily某天小于monthly的某天时，开始累加成交量x，
                sum = sum.add(dayK.getVolume());
            }
            BaseUtils.writeStockKLine(STD_MONTHLY_PATH + stock, newMonthList);
            System.out.println("fix finish: " + stock);
        }
    }
}
