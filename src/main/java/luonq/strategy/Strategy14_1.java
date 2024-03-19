package luonq.strategy;

import bean.StockKLine;
import bean.Total;
import com.google.common.collect.Lists;
import luonq.data.ReadFromDB;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 计算历史光脚阴线第二天上涨的概率（光脚阴线每个股票比例不一样），找成功率高的收盘前买入第二天开盘立即卖出（计算单只股票明细）
 */
@Component
public class Strategy14_1 {

    @Autowired
    private ReadFromDB readFromDB;

    private List<Total> codeData;
    private Map<String, List<Total>> codeTotalMap;
    private List<StockKLine> stockKLines = Lists.newArrayList();
    private Set<String> allCode;

    private void init() {
        codeData = readFromDB.getCodeDate("2023", "VOYA", "asc");
        codeTotalMap = codeData.stream().collect(Collectors.groupingBy(Total::getCode, Collectors.toList()));
        stockKLines = codeData.stream().map(Total::toKLine).collect(Collectors.toList());
    }

    public void main() throws Exception {
        init();

        double successCount = 0, failCount = 0;
        for (int i = 1; i < stockKLines.size() - 1; i++) {
            StockKLine kLine = stockKLines.get(i);
            String date = kLine.getDate();
            //                BOLL boll = dateToBollMap.get(date);
            //                if (boll == null) {
            //                    continue;
            //                }

            StockKLine prevKLine = stockKLines.get(i - 1);
            StockKLine nextKLine = stockKLines.get(i + 1);
            //                BOLL prevBoll = dateToBollMap.get(prevKLine.getDate());

            double open = kLine.getOpen();
            double close = kLine.getClose();
            double high = kLine.getHigh();
            double low = kLine.getLow();
            BigDecimal volume = kLine.getVolume();

            double prevOpen = prevKLine.getOpen();
            double prevClose = prevKLine.getClose();
            double prevLow = prevKLine.getLow();
            BigDecimal prevVolume = prevKLine.getVolume();
            //                double prevDn = prevBoll.getDn();

            double nextOpen = nextKLine.getOpen();
            double nextClose = nextKLine.getClose();
            //                double dn = boll.getDn();

            // 前一天上涨，则跳过
            if (prevClose > prevOpen) {
                continue;
            }

            double prevLowCloseDiff = (prevClose - prevOpen) / (prevLow - prevOpen) * 100;
            double openPrevCloseDiff = ((open / prevClose) - 1) * 100;
            if (prevLowCloseDiff < 90) {
//                continue;
            }

            if (open >= prevClose) {
                successCount++;
                System.out.println(date + "\tgain\tprevLowCloseDiff=" + prevLowCloseDiff + "\topenPrevCloseDiff=" + openPrevCloseDiff);
            } else {
                failCount++;
                System.out.println(date + "\tloss\tprevLowCloseDiff=" + prevLowCloseDiff + "\topenPrevCloseDiff=" + openPrevCloseDiff);
            }
        }
        double successRatio = successCount / (successCount + failCount) * 100;
        System.out.println(successRatio);
    }
}
