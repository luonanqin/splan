package luonq.stock;

import bean.BOLL;
import bean.StockKLine;
import com.google.common.collect.Lists;
import org.apache.commons.collections4.CollectionUtils;
import util.BaseUtils;
import util.Constants;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.math.BigDecimal.ROUND_DOWN;
import static java.math.BigDecimal.ROUND_HALF_UP;

/**
 * Created by Luonanqin on 2023/2/3.
 * 读取前一年和当年的k线计算当年布林线，新年前19个交易日需要前一年的数据一起计算布林线
 * 计算好的布林线要merge到mergeBoll
 */
public class OpenBollingerForYear {

    public static void calculate() throws Exception {
        String mergePath = Constants.HIS_BASE_PATH + "merge/";
        Map<String, String> stockToKLineMap = BaseUtils.getFileMap(mergePath);

        LocalDate today = LocalDate.now();
        int curYear = today.getYear(), lastYear = curYear - 1;
        String lastKLinePath, curKLinePath;
        if (curYear < 2024) {
            return;
        }
        LocalDate firstWorkDay = BaseUtils.getFirstWorkDay();
        String bollPath;
        if (curYear == 2023) {
            bollPath = Constants.HIS_BASE_PATH + "bollWithOpen/";
        } else {
            if (today.isAfter(firstWorkDay)) {
                bollPath = Constants.HIS_BASE_PATH + curYear + "/openBOLL/";
            } else {
                if (lastYear == 2023) {
                    bollPath = Constants.HIS_BASE_PATH + "bollWithOpen/";
                } else {
                    bollPath = Constants.HIS_BASE_PATH + lastYear + "/openBOLL/";
                }
            }
        }

        if (curYear == 2024) {
            lastKLinePath = Constants.HIS_BASE_PATH + "2023daily/";
            curKLinePath = Constants.HIS_BASE_PATH + curYear + "/dailyKLine/";
        } else {
            lastKLinePath = Constants.HIS_BASE_PATH + lastYear + "/dailyKLine/";
            curKLinePath = Constants.HIS_BASE_PATH + curYear + "/dailyKLine/";
        }

        for (String stock : stockToKLineMap.keySet()) {
            if (!stock.equals("AAPL")) {
//                continue;
            }

            List<StockKLine> lastKLines = BaseUtils.loadDataToKline(lastKLinePath + stock, lastYear);
            List<StockKLine> curKLines = BaseUtils.loadDataToKline(curKLinePath + stock, curYear);

            List<StockKLine> kLines = Lists.newArrayList();
            kLines.addAll(curKLines);
            kLines.addAll(lastKLines);
            if (CollectionUtils.isEmpty(kLines)) {
                continue;
            }

            List<BOLL> curBolls = BaseUtils.readBollFile(bollPath + stock, curYear);
            List<String> klineDateList = kLines.stream().map(StockKLine::getDate).collect(Collectors.toList());
            Set<String> bollDateSet = curBolls.stream().map(BOLL::getDate).collect(Collectors.toSet());
            int index = 0;
            if (CollectionUtils.isEmpty(curBolls)) {
                if (!klineDateList.get(0).contains(String.valueOf(curYear))) {
                    continue;
                }
                index = curKLines.size() - 19;
            } else {
                for (; index < kLines.size(); index++) {
                    String klineDate = klineDateList.get(index);
                    if (bollDateSet.contains(klineDate)) {
                        break;
                    }
                }
            }
            if (index == 0 || kLines.size() < (19 + index)) {
                continue;
            }

            BigDecimal m20close = BigDecimal.ZERO;
            int ma20count = 0;
            double md, mb, up, dn;
            List<String> bollList = curBolls.stream().map(BOLL::toString).collect(Collectors.toList());
            List<String> newBollList = Lists.newArrayList();
            for (int i = 19 + index - 1; i >= 0; i--) {
                StockKLine kLine = kLines.get(i);
                String date = kLine.getDate();

                BigDecimal close = BigDecimal.valueOf(kLine.getClose());
                BigDecimal open = BigDecimal.valueOf(kLine.getOpen());
                ma20count++;
                if (ma20count < 20) {
                    m20close = m20close.add(close);
                    continue;
                } else {
                    m20close = m20close.add(open);
                }

                double ma20 = m20close.divide(BigDecimal.valueOf(20), 2, ROUND_HALF_UP).doubleValue();
                mb = ma20;
                BigDecimal avgDiffSum = BigDecimal.ZERO;
                int j = i, times = 20;
                while (times > 0) {
                    double c;
                    if (j == i) {
                        c = kLines.get(j).getOpen();
                    } else {
                        c = kLines.get(j).getClose();
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
                m20close = m20close.subtract(BigDecimal.valueOf(kLines.get(i + 20 - 1).getClose()));
                m20close = m20close.subtract(open).add(close);

                BOLL boll = BOLL.builder().date(date).md(md).mb(mb).up(up).dn(dn).build();
                newBollList.add(0, boll.toString());
            }

            if (CollectionUtils.isNotEmpty(newBollList)) {
                bollList.addAll(0, newBollList);
                BaseUtils.writeFile(Constants.HIS_BASE_PATH + curYear + "/openBOLL/" + stock, bollList);

                //                log.info("finish " + stock);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        calculate();
    }
}
