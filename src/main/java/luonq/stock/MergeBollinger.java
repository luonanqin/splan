package luonq.stock;

import bean.BOLL;
import bean.StockKLine;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import util.BaseUtils;
import util.Constants;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.math.BigDecimal.ROUND_DOWN;
import static java.math.BigDecimal.ROUND_HALF_UP;

/**
 * Created by Luonanqin on 2023/2/3.
 */
@Slf4j
public class MergeBollinger {

    public static void calculate() throws Exception {
        String mergePath = Constants.HIS_BASE_PATH + "merge/";
        Map<String, String> stockToKLineMap = BaseUtils.getFileMap(mergePath);
        Map<String, String> hasCalcMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "mergeBoll");
        Set<String> hasCalcStock = hasCalcMap.keySet();
        int year = 2023;

        for (String stock : stockToKLineMap.keySet()) {
            if (!stock.equals("AAPL")) {
//                                continue;
            }
            if (hasCalcStock.contains(stock)) {
                //                continue;
            }
            List<StockKLine> stockKLines = BaseUtils.loadDataToKline(stockToKLineMap.get(stock), year);
            List<BOLL> bolls = BaseUtils.readBollFile(Constants.HIS_BASE_PATH + "mergeBoll/" + stock, year);
            if (CollectionUtils.isEmpty(bolls) || CollectionUtils.isEmpty(stockKLines)) {
                continue;
            }
            List<String> stockDateList = stockKLines.stream().filter(s -> s.getDate().endsWith(String.valueOf(year))).map(StockKLine::getDate).collect(Collectors.toList());
            Set<String> bollDateSet = bolls.stream().filter(b -> b.getDate().endsWith(String.valueOf(year))).map(BOLL::getDate).collect(Collectors.toSet());
            String earliestDate = "", prevDate = "";
            int index = 0;
            for (int i = 0; i < stockDateList.size(); i++) {
                String stockDate = stockDateList.get(i);
                if (!bollDateSet.contains(stockDate)) {
                    earliestDate = stockDate;
                    prevDate = stockDateList.get(i + 1);
                    index = i;
                }
            }
            if (StringUtils.isBlank(earliestDate)) {
                //                log.info("has calculate: " + stock);
                continue;
            }

            BigDecimal m20close = BigDecimal.ZERO;
            int ma20count = 0;
            double md = 0, mb = 0, up = 0, dn = 0;

            for (int i = 0; i < bolls.size(); i++) {
                BOLL boll = bolls.get(i);
                if (boll.getDate().equals(prevDate)) {
                    if (i == 0) {
                        break;
                    }
                    bolls = bolls.subList(i, bolls.size());
                    break;
                }
            }
            List<String> bollList = bolls.stream().map(BOLL::toString).collect(Collectors.toList());
            for (int i = 19 + index; i >= 0; i--) {
                StockKLine kLine = stockKLines.get(i);
                String date = kLine.getDate();

                BigDecimal close = BigDecimal.valueOf(kLine.getClose());
                m20close = m20close.add(close);
                ma20count++;
                if (ma20count < 20) {
                    continue;
                }

                double ma20 = m20close.divide(BigDecimal.valueOf(20), 2, ROUND_HALF_UP).doubleValue();
                mb = ma20;
                BigDecimal avgDiffSum = BigDecimal.ZERO;
                int j = i, times = 20;
                while (times > 0) {
                    double c = stockKLines.get(j++).getClose();
                    avgDiffSum = avgDiffSum.add(BigDecimal.valueOf(c - ma20).pow(2));
                    times--;
                }

                md = Math.sqrt(avgDiffSum.doubleValue() / 20);
                BigDecimal mdPow2 = BigDecimal.valueOf(md).multiply(BigDecimal.valueOf(2));
                up = BigDecimal.valueOf(mb).add(mdPow2).setScale(3, ROUND_DOWN).doubleValue();
                dn = BigDecimal.valueOf(mb).subtract(mdPow2).setScale(3, ROUND_DOWN).doubleValue();

                ma20count--;
                m20close = m20close.subtract(BigDecimal.valueOf(stockKLines.get(i + 20 - 1).getClose()));

                BOLL boll = BOLL.builder().date(date).md(md).mb(mb).up(up).dn(dn).build();
                bollList.add(0, boll.toString());
            }

            BaseUtils.writeFile(Constants.HIS_BASE_PATH + "mergeBoll/" + stock, bollList);
            //            log.info("finish " + stock);
        }
    }

    public static void main(String[] args) throws Exception {
        calculate();
    }
}
