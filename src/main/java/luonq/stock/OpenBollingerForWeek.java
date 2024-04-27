package luonq.stock;

import bean.BOLL;
import bean.StockKLine;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import util.BaseUtils;

import java.math.BigDecimal;
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
@Slf4j
public class OpenBollingerForWeek {

    public static void calculate() throws Exception {
        Map<String, String> stockToKLineMap = BaseUtils.getFileMap("/Users/Luonanqin/study/intellij_idea_workspaces/temp/week/");
        String bollPath = "/Users/Luonanqin/study/intellij_idea_workspaces/temp/weekopenboll/";
        Map<String, String> stockToBollMap = BaseUtils.getFileMap(bollPath);

        for (String stock : stockToKLineMap.keySet()) {
            if (!stock.equals("AAPL")) {
//                continue;
            }

            List<StockKLine> kLines = Lists.newArrayList();
            String kLineFilePath = stockToKLineMap.get(stock);
            String bollFilePath = stockToBollMap.get(stock);
            if (StringUtils.isAnyBlank(kLineFilePath)) {
                continue;
            }

            List<String> lines = BaseUtils.readFile(kLineFilePath);
            List<BOLL> bollList = BaseUtils.readBollFile(bollFilePath, 2024, 1990);

            for (String line : lines) {
                String[] split = line.split("\t");
                String date = BaseUtils.unformatDate(split[0]);
                double open = Double.valueOf(split[1]);
                double close = Double.valueOf(split[2]);
                double high = Double.valueOf(split[3]);
                double low = Double.valueOf(split[4]);
                BigDecimal volume = BigDecimal.valueOf(Long.valueOf(split[5]));
                StockKLine kLine = new StockKLine();
                kLine.setDate(date);
                kLine.setOpen(open);
                kLine.setClose(close);
                kLine.setHigh(high);
                kLine.setLow(low);
                kLine.setVolume(volume);

                kLines.add(kLine);
            }

            List<String> klineDateList = kLines.stream().map(StockKLine::getDate).collect(Collectors.toList());
            Set<String> bollDateSet = bollList.stream().map(BOLL::getDate).collect(Collectors.toSet());
            int index = 0;
            if (CollectionUtils.isEmpty(bollList)) {
                index = klineDateList.size() - 19;
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
            List<String> bollLines = bollList.stream().map(BOLL::toString).collect(Collectors.toList());
            List<String> newBollLnes = Lists.newArrayList();
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
                newBollLnes.add(0, boll.toString());
            }

            if (CollectionUtils.isNotEmpty(newBollLnes)) {
                bollLines.addAll(0, newBollLnes);
                BaseUtils.writeFile(bollPath + stock, bollLines);

                //                log.info("finish " + stock);
            }
        }
    }

    public static void main(String[] args) throws Exception {
        calculate();
    }
}
