package luonq.stock;

import bean.StockKLine;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import util.BaseUtils;
import util.Constants;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * Created by Luonanqin on 2023/4/25.
 */
@Slf4j
public class MergeKline {

    public static void merge() throws Exception {
        String mergePath = Constants.HIS_BASE_PATH + "merge/";
        Map<String, String> hasMergeMap = BaseUtils.getFileMap(mergePath);

        Map<String, String> dailyFileMap = BaseUtils.getFileMap(Constants.STD_DAILY_PATH);
        List<String> stockList = Lists.newArrayList(dailyFileMap.keySet());
        //        stockList.clear();
        //        stockList.add("FUTU");

        // 计算读取最新k线的目录及读取年份
        LocalDate firstWorkDay = BaseUtils.getFirstWorkDay();
        LocalDate today = LocalDate.now();
        int year = today.getYear(), beforeYear = year;
        String stockKLinePath;
        if (year == 2023) {
            stockKLinePath = Constants.HIS_BASE_PATH + "2023daily";
        } else {
            if (today.isAfter(firstWorkDay)) {
                stockKLinePath = Constants.HIS_BASE_PATH + year + "/dailyKLine";
            } else {
                if (year - 1 == 2023) {
                    stockKLinePath = Constants.HIS_BASE_PATH + "2023daily/";
                } else {
                    stockKLinePath = Constants.HIS_BASE_PATH + (year - 1) + "/dailyKLine";
                }
            }
        }

        Map<String, String> stockKLineMap = BaseUtils.getFileMap(stockKLinePath);
        for (String stock : stockList) {
            if (!stockKLineMap.containsKey(stock)) {
                continue;
            }
            String mergedFile = hasMergeMap.get(stock);
            if (StringUtils.isBlank(mergedFile)) {
                String dailyFilePath = dailyFileMap.get(stock);
                List<StockKLine> dailyStockKLines = BaseUtils.loadDataToKline(dailyFilePath, 2022);
                mergedFile = mergePath + stock;
                BaseUtils.writeStockKLine(mergedFile, dailyStockKLines);
            }
            StockKLine latestKLine = BaseUtils.getLatestKLine(mergedFile);

            String filePath = stockKLineMap.get(stock);
            List<StockKLine> hisStockKLines = BaseUtils.loadDataToKline(filePath, beforeYear);

            int index = 0;
            for (; index < hisStockKLines.size(); index++) {
                if (StringUtils.equals(latestKLine.toString(), hisStockKLines.get(index).toString())) {
                    break;
                }
            }
            if (index == 0) {
                continue;
            }
            hisStockKLines = hisStockKLines.subList(0, index);
            List<StockKLine> stockKLines = BaseUtils.loadDataToKline(mergedFile, beforeYear);
            hisStockKLines.addAll(stockKLines);

            BaseUtils.writeStockKLine(mergePath + stock, hisStockKLines);
            //            log.info("finish " + stock);
        }
    }

    public static void main(String[] args) throws Exception {
        merge();
    }
}
