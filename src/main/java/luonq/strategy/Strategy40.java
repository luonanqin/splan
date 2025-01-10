package luonq.strategy;

import bean.StockKLine;
import com.google.common.collect.Lists;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
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
 * 1.获取每周最后一天的开盘涨幅绝对值，计算其值有几个标准差，从大到小倒排并取前十
 * 2.获取每周前3或4天的期权成交量，计算其标准差
 * 3.获取每周前3或4天的开盘涨跌幅，计算其绝对值的标准差
 */
public class Strategy40 {

    public static void main(String[] args) throws Exception {
        Map<String, String> fileMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "2024/dailyKLine");

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
                List<StockKLine> list = Lists.newArrayList();
                String _1 = start.format(DB_DATE_FORMATTER);
                while (true) {
                    if (start.getDayOfWeek().getValue() >= 7) {
                        start = start.plusDays(1);
                        break;
                    }
                    StockKLine kLine = stockMap.get(_1);
                    start = start.plusDays(1);
                    _1 = start.format(DB_DATE_FORMATTER);
                    if (kLine != null) {
                        list.add(kLine);
                    }
                }

                double[] dataArray = new double[list.size() - 1];

                for (int j = 0; j < list.size() - 1; j++) {
                    double diff = (list.get(j).getOpen() - lastClose) / lastClose;
                    dataArray[j] = Math.abs(diff);
                    lastClose = list.get(j).getClose();
                }

                StandardDeviation stdev = new StandardDeviation();
                double stdevV = stdev.evaluate(dataArray);
                System.out.println(stdevV);
            }
        }
    }
}
