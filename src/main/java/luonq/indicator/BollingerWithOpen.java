package luonq.indicator;

import bean.BOLL;
import bean.StockKLine;
import com.google.common.collect.Lists;
import org.apache.commons.collections4.CollectionUtils;
import util.BaseUtils;
import util.Constants;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static java.math.BigDecimal.ROUND_DOWN;
import static java.math.BigDecimal.ROUND_HALF_UP;

/**
 * Created by Luonanqin on 2023/2/3.
 */
public class BollingerWithOpen {

    public static void main(String[] args) throws Exception {
        calculate();
    }

    public static void calculate() throws Exception {
        String mergePath = Constants.HIS_BASE_PATH + "merge/";
        Map<String, String> stockToKLineMap = BaseUtils.getFileMap(mergePath);

        for (String stock : stockToKLineMap.keySet()) {
            if (!stock.equals("EBC")) {
//                continue;
            }
            String filePath = stockToKLineMap.get(stock);
            List<StockKLine> stockKLines = BaseUtils.loadDataToKline(filePath, 2023);
            List<BOLL> bolls = BaseUtils.readBollFile(Constants.HIS_BASE_PATH + "bollWithOpen/" + stock, 2023, 0);

            List<String> bollList = Lists.newArrayList();
            if (CollectionUtils.isNotEmpty(bolls)) {
                bollList.addAll(bolls.stream().map(BOLL::toString).collect(Collectors.toList()));
                BOLL latestBoll = bolls.get(0);
                String bollDate = latestBoll.getDate();
                for (int i = 0; i < stockKLines.size(); i++) {
                    StockKLine kLine = stockKLines.get(i);
                    if (kLine.getDate().equals(bollDate)) {
                        if (i == 0) {
                            stockKLines.clear();
                            break;
                        }
                        int to = (i + 19) > stockKLines.size() ? stockKLines.size() : (i + 19);
                        stockKLines = stockKLines.subList(0, to);
                        break;
                    }
                }
            }
            if (stockKLines.isEmpty()) {
//                log.info("has calculate " + stock);
                continue;
            }

            BigDecimal m20close = BigDecimal.ZERO;
            int ma20count = 0;
            double md = 0, mb = 0, up = 0, dn = 0;
            for (int i = stockKLines.size() - 1; i >= 0; i--) {
                StockKLine kLine = stockKLines.get(i);
                String date = kLine.getDate();

                BigDecimal open = BigDecimal.valueOf(kLine.getOpen());
                BigDecimal close = BigDecimal.valueOf(kLine.getClose());
                m20close = m20close.add(close);
                ma20count++;

                if (ma20count == 20) {
                    m20close = m20close.subtract(close).add(open);
                    double ma20 = m20close.divide(BigDecimal.valueOf(20), 2, ROUND_HALF_UP).doubleValue();
                    mb = ma20;
                    BigDecimal avgDiffSum = BigDecimal.ZERO;
                    int j = i, times = 20;
                    while (times > 0) {
                        double c;
                        if (j == i) {
                            c = stockKLines.get(j).getOpen();
                        } else {
                            c = stockKLines.get(j).getClose();
                        }
                        j++;
                        avgDiffSum = avgDiffSum.add(BigDecimal.valueOf(c - ma20).pow(2));
                        times--;
                    }

                    md = Math.sqrt(avgDiffSum.doubleValue() / 20);
                    BigDecimal mdPow2 = BigDecimal.valueOf(md).multiply(BigDecimal.valueOf(2));
                    up = BigDecimal.valueOf(mb).add(mdPow2).setScale(3, ROUND_DOWN).doubleValue();
                    dn = BigDecimal.valueOf(mb).subtract(mdPow2).setScale(3, ROUND_DOWN).doubleValue();

                    ma20count--;
                    m20close = m20close.subtract(BigDecimal.valueOf(stockKLines.get(i + 20 - 1).getClose()));
                    m20close = m20close.subtract(open).add(close);
                }
                if (md == 0) {
                    continue;
                }
                BOLL boll = BOLL.builder().date(date).md(md).mb(mb).up(up).dn(dn).build();
                bollList.add(0, boll.toString());
            }

            BaseUtils.writeFile(Constants.HIS_BASE_PATH + "bollWithOpen/" + stock, bollList);
//            log.info("finish " + stock);
        }
    }
}
