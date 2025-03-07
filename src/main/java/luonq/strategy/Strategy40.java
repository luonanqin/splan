package luonq.strategy;

import bean.StockKLine;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import luonq.data.ReadFromDB;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import util.BaseUtils;
import util.Constants;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static util.Constants.DB_DATE_FORMATTER;

/**
 * // * 1.获取每周最后一天的开盘涨幅绝对值，计算其值有几个标准差，从大到小倒排并取前十
 * // * 2.获取每周前3或4天的期权成交量，计算其标准差
 * 3.获取每周前3或4天的振幅，计算其标准差
 */
@Component
public class Strategy40 {

    @Autowired
    private ReadFromDB readFromDB;

    public void test() throws Exception {
        Map<String, String> fileMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "2024/dailyKLine");
        List<StockKLine> aapl = BaseUtils.loadDataToKline(Constants.HIS_BASE_PATH + "2024/dailyKLine/AAPL", 2024, 2023);
        Map<String, Set<String>> dateToEarningStockMap = Maps.newHashMap();
        for (StockKLine stockKLine : aapl) {
            String formatDate = stockKLine.getFormatDate();
            List<String> stocks = readFromDB.getStockForEarning(formatDate);
            dateToEarningStockMap.put(formatDate, Sets.newHashSet(stocks));
        }

        Set<String> optionStocks = BaseUtils.getPennyOptionStock();

        for (String stock : optionStocks) {
            //            Map<String, Map<String, OptionDaily>> optionDailyMap = BaseUtils.loadOptionDailyMap(stock);
            String path = fileMap.get(stock);
            List<StockKLine> stockKLines = BaseUtils.loadDataToKline(path, 2024, 2023);
            if (stockKLines.size() < 10) {
                continue;
            }

            Collections.reverse(stockKLines);
            Map<String, StockKLine> stockMap = stockKLines.stream().collect(Collectors.toMap(StockKLine::getFormatDate, Function.identity()));
            int i = 0;
            String startDate = null;
            double lastClose = 0d;
            for (; i < stockKLines.size(); i++) {
                String curDate = stockKLines.get(i).getFormatDate();
                LocalDate curLd = LocalDate.parse(curDate, DB_DATE_FORMATTER);
                int cur = curLd.getDayOfWeek().getValue();

                String nextDate = stockKLines.get(i + 1).getFormatDate();
                LocalDate nextLd = LocalDate.parse(nextDate, DB_DATE_FORMATTER);
                int next = nextLd.getDayOfWeek().getValue();

                if (next < cur) {
                    lastClose = stockKLines.get(i).getClose();
                    startDate = nextDate;
                    i++;
                    break;
                }
            }



            LocalDate start = LocalDate.parse(startDate, DB_DATE_FORMATTER);
            while (true) {
                if (start.getYear() > 2024) {
                    break;
                }
                List<StockKLine> kLineList = Lists.newArrayList();
                List<Double> swingList = Lists.newArrayList();
                String _1 = start.format(DB_DATE_FORMATTER);
                double lastClose2 = lastClose;
                while (true) {
                    if (start.getDayOfWeek().getValue() >= 7) {
                        start = start.plusDays(1);
                        break;
                    }
                    StockKLine kLine = stockMap.get(_1);
                    start = start.plusDays(1);
                    _1 = start.format(DB_DATE_FORMATTER);
                    if (kLine != null) {
                        kLineList.add(kLine);
                        double high = kLine.getHigh();
                        double low = kLine.getLow();
                        double swing = (high - low) / lastClose2;
                        swingList.add(swing);
                        lastClose2 = kLine.getClose();
                    }
                }
                //                if (kLineList.size() < 2) {
                //                    lastClose = kLineList.get(kLineList.size() - 1).getClose();
                //                    continue;
                //                }
                if (CollectionUtils.isEmpty(kLineList)) {
                    continue;
                }

                double[] openDiffArray = new double[kLineList.size() - 1];
                double[] swingArray = new double[kLineList.size() - 1];

                for (int j = 0; j < kLineList.size() - 1; j++) {
                    openDiffArray[j] = Math.abs((kLineList.get(j).getOpen() - lastClose) / lastClose);
                    swingArray[j] = swingList.get(j);
                    lastClose = kLineList.get(j).getClose();
                }

                StandardDeviation stdev = new StandardDeviation();
                // 每周前几天开盘价的标准差
                double openDiffStd = stdev.evaluate(openDiffArray);
                // 每周前几天振幅的标准差
                double swingStd = stdev.evaluate(swingArray);

                //
                StockKLine lastKLine = kLineList.get(kLineList.size() - 2);
                Set<String> earningStocks = dateToEarningStockMap.get(lastKLine.getFormatDate());
                if (earningStocks.contains(stock)) {
                    break;
                }

                // 每周最后一天的开盘涨跌幅
                lastClose = lastKLine.getClose();
                StockKLine curKLine = kLineList.get(kLineList.size() - 1);
                double finalOpen = curKLine.getOpen();
                double finalOpenDiff = Math.abs((finalOpen - lastClose) / lastClose);

                // 每周最后一天的波幅，(最高-最低)/前日收盘
                double high = curKLine.getHigh();
                double low = curKLine.getLow();
                double swing = (high - low) / lastClose;

                if (finalOpen > 20 && finalOpenDiff > 0.03) {
                    //                    if (finalOpenDiff > 0.05) {
                    //                if (swingStd < 0.01 && finalOpenDiff > 0.02) {
                    System.out.println(stock + "\t" + curKLine.getFormatDate() + "\t" + finalOpen + "\t" + openDiffStd + "\t" + swingStd + "\t" + finalOpenDiff + "\t" + swing);
                }
            }
        }
    }
}
