package luonq.strategy;

import bean.StockKLine;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import util.BaseUtils;
import util.Constants;

import java.time.LocalDate;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Strategy40 {

    public static void main(String[] args) throws Exception {
        Map<String, String> fileMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "2024/dailyKLine");

        for (String stock : fileMap.keySet()) {
            String path = fileMap.get(stock);
            List<StockKLine> stockKLines = BaseUtils.loadDataToKline(path, 2024, 2023);
            if (stockKLines.size() < 10) {
                continue;
            }

            Collections.reverse(stockKLines);
            Map<String, StockKLine> stockMap = stockKLines.stream().collect(Collectors.toMap(StockKLine::getFormatDate, Function.identity()));

            for (int i = 0; i < stockKLines.size(); i++) {
                String mon = stockKLines.get(i).getFormatDate();
                LocalDate ld = LocalDate.parse(mon, Constants.DB_DATE_FORMATTER);

                int dayOfWeek = ld.getDayOfWeek().getValue();
                if (dayOfWeek != 1) {
                    continue;
                }
                if (i + 2 >= stockKLines.size()) {
                    break;
                }
                String tue = stockKLines.get(i + 1).getFormatDate();
                String wed = stockKLines.get(i + 2).getFormatDate();

                StockKLine monS = stockMap.get(mon);
                StockKLine tueS = stockMap.get(tue);
                StockKLine wedS = stockMap.get(wed);
                double monV = monS.getVolume().doubleValue();
                double tueV = tueS.getVolume().doubleValue();
                double wedV = wedS.getVolume().doubleValue();

                StandardDeviation stdev = new StandardDeviation();
                double stdevV = stdev.evaluate(new double[] { monV, tueV, wedV });
                System.out.println(stdevV);
            }
        }
    }
}
