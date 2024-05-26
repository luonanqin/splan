package luonq.strategy;

import bean.StockKLine;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import org.apache.commons.collections4.CollectionUtils;
import org.slf4j.LoggerFactory;
import util.BaseUtils;
import util.Constants;

import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 以05-23号的收盘价作为输入，获取24号的期权代码
 */
public class Strategy32 {

    public static void main(String[] args) throws Exception {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.http").setLevel(Level.INFO);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("httpclient.wire").setLevel(Level.INFO);

        Map<String, String> klineFileMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "2024/dailyKLine");
        int year = 2024;
        Set<String> optionStock = BaseUtils.getOptionStock();
        for (String stock : optionStock) {
            String filePath = klineFileMap.get(stock);

            List<StockKLine> stockKLines = BaseUtils.loadDataToKline(filePath, year, year - 1);
            if (CollectionUtils.isEmpty(stockKLines)) {
                continue;
            }
            StockKLine stockKLine = stockKLines.get(0);
            String date = stockKLine.getFormatDate();
            if (!date.equals("2024-05-23")) {
                continue;
            }

            double close = stockKLine.getClose();
            List<String> optionCodeList = Strategy28.getOptionCode(stock, close, date);
            for (String optionCode : optionCodeList) {
                if (optionCode.contains("240524")) {
                    System.out.println(stock + "\t" + optionCode);
                    break;
                }
            }
        }
    }
}
