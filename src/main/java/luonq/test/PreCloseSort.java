package luonq.test;

import bean.BOLL;
import bean.SimpleTrade;
import bean.StockKLine;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.collections4.CollectionUtils;
import luonq.strategy.OverBollingerDN2023Real;
import util.BaseUtils;
import util.Constants;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Created by Luonanqin on 2023/5/16.
 */
public class PreCloseSort {

    public static void main(String[] args) throws Exception {
        int beforeYear = 2023, afterYear = 2021, afterYear2 = 2022, historyBeforeYear = 2022;
        Map<String, OverBollingerDN2023Real.StockRatio> originRatioMap = OverBollingerDN2023Real.computeHistoricalOverBollingerRatio(historyBeforeYear);
        originRatioMap.remove("STAR");
        originRatioMap.remove("OCSL");

        Map<String, Map<String, BOLL>> dateToStockBollMap = Maps.newHashMap();
        for (String stock : originRatioMap.keySet()) {
            //            if (StringUtils.isNotBlank(TEST_STOCK) && !stock.equals(TEST_STOCK)) {
            //                continue;
            //            }
            List<BOLL> bolls = BaseUtils.readBollFile(Constants.HIS_BASE_PATH + "mergeBoll/" + stock, beforeYear, afterYear2);

            for (BOLL boll : bolls) {
                String date = boll.getDate();
                if (!dateToStockBollMap.containsKey(date)) {
                    dateToStockBollMap.put(date, Maps.newHashMap());
                }
                dateToStockBollMap.get(date).put(stock, boll);
            }
        }

        List<StockKLine> kLines = BaseUtils.loadDataToKline(Constants.HIS_BASE_PATH + "merge/AAPL", beforeYear, afterYear);
        Map<String, List<String>> dateToBefore20dayMap = Maps.newHashMap();
        List<String> dateList = Lists.newArrayList();
        for (int i = 0; i < kLines.size(); i++) {
            if (i + 20 > kLines.size() - 1) {
                break;
            }
            String date = kLines.get(i).getDate();
            List<String> list = Lists.newArrayList();
            for (int j = 1; j < 20; j++) {
                list.add(kLines.get(i + j).getDate());
            }
            dateToBefore20dayMap.put(date, list);

            String year = date.substring(date.lastIndexOf("/") + 1);
            if (year.equals(String.valueOf(beforeYear))) {
                dateList.add(date);
            }
        }
        Collections.reverse(dateList);
        dateList = dateList.subList(0, 75);

        Map<String, Map<String, SimpleTrade>> dateToPreCloseMap = Maps.newHashMap();
        Map<String, String> preCloseFileMap = BaseUtils.getFileMap(Constants.TRADE_PATH + "preClose");
        for (String stock : preCloseFileMap.keySet()) {
            List<String> lines = BaseUtils.readFile(preCloseFileMap.get(stock));
            if (CollectionUtils.isEmpty(lines)) {
                continue;
            }

            lines.remove(lines.size() - 1);
            for (String line : lines) {
                String[] split = line.split("\t");
                String date = split[0];
                double price = Double.parseDouble(split[1]);
                if (!dateToPreCloseMap.containsKey(date)) {
                    dateToPreCloseMap.put(date, Maps.newHashMap());
                }
                SimpleTrade preClose = new SimpleTrade();
                preClose.setCode(stock);
                preClose.setDate(date);
                preClose.setTradePrice(price);

                dateToPreCloseMap.get(date).put(stock, preClose);
            }
        }

        Map<String, List<String>> dateToStocksMap = Maps.newHashMap();
        for (String date : dateToStockBollMap.keySet()) {
            Map<String, SimpleTrade> stockToPreCloseMap = dateToPreCloseMap.get(date);
            if (stockToPreCloseMap == null) {
                continue;
            }

            Map<String, BOLL> stockToBollMap = dateToStockBollMap.get(date);
            Map<String, Double> stockToRatioMap = Maps.newHashMap();
            for (String stock : stockToPreCloseMap.keySet()) {
                SimpleTrade preClose = stockToPreCloseMap.get(stock);
                BOLL boll = stockToBollMap.get(stock);
                if (boll != null && boll.getDn() > 0) {
                    double dn = boll.getDn();
                    double open = preClose.getTradePrice();
                    double ratio = (dn - open) / dn;
                    if (ratio < 0 || open < 7) {
                        continue;
                    }
                    if (date.equals("01/03/2023") && (stock.equals("MSI") || stock.equals("GFF"))) {
                        System.out.println(stock + " " + dn + " " + open + " " + ratio);
                    }
                    stockToRatioMap.put(stock, ratio);
                }
            }
            List<String> stocks = stockToRatioMap.entrySet().stream().sorted((o1, o2) -> {
                if (o1.getValue() < o2.getValue()) {
                    return 1;
                }
                return -1;
            }).map(o -> o.getKey()).collect(Collectors.toList());
            dateToStocksMap.put(date, stocks);
        }

        for (String date : dateList) {
            List<String> stock = dateToStocksMap.get(date);
            if (CollectionUtils.isNotEmpty(stock)) {
                System.out.println(date + ": " + (stock.size() > 10 ? stock.subList(0, 10) : stock));
            }
        }

    }
}
