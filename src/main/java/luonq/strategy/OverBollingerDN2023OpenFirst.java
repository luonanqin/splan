package luonq.strategy;

import bean.BOLL;
import bean.SimpleTrade;
import bean.StockKLine;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.Data;
import luonq.stock.FilterStock;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import util.BaseUtils;
import util.Constants;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.math.BigDecimal.ROUND_DOWN;
import static java.math.BigDecimal.ROUND_HALF_UP;

/**
 * Created by Luonanqin on 2023/3/21.
 */
public class OverBollingerDN2023OpenFirst {

    public static final String TEST_STOCK = "";
    public static final Set<String> SKIP_SET = Sets.newHashSet("FRC", "SIVBQ");

    @Data
    public static class Bean implements Serializable {
        String date;
        private double open;
        private double close;
        private double high;
        private double low;
        private double dn;
        private double changePnt;
        private double lowDnDiffPnt;
        private double highCloseDiffPnt;
        private double openDnDiffPnt;
        private double closeUpDiffPnt;
        private int closeLessOpen; // true=1 false=0

        public String toString() {
            return String.format("%s\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f", date, open, close, high, low, dn, lowDnDiffPnt, highCloseDiffPnt);
        }
    }

    @Data
    public static class RatioBean implements Serializable {
        List<Bean> beanList = Lists.newArrayList();
        double ratio;

        public void add(Bean bean) {
            beanList.add(bean);
            long trueCount = beanList.stream().filter(c -> c.getCloseLessOpen() == 1).count();
            int count = beanList.size();
            ratio = (double) trueCount / count;
        }
    }

    @Data
    public static class StockRatio implements Serializable {
        Map<Integer, RatioBean> ratioMap = Maps.newHashMap();

        public void addBean(Bean bean) {
            double dn = bean.getDn();
            double low = bean.getLow();
            double open = bean.getOpen();
            if (!(low < dn && open < dn)) {
                return;
            }

            double openDnDiffPnt = bean.getOpenDnDiffPnt();
            int openDnDiffRange = (int) openDnDiffPnt;
            if (openDnDiffRange < 0) {
                return;
            }
            if (openDnDiffRange > 6) {
                if (!ratioMap.containsKey(6)) {
                    ratioMap.put(6, new RatioBean());
                }
                ratioMap.get(6).add(bean);
            } else if (ratioMap.containsKey(openDnDiffRange)) {
                ratioMap.get(openDnDiffRange).add(bean);
            } else {
                RatioBean ratioBean = new RatioBean();
                ratioBean.add(bean);
                ratioMap.put(openDnDiffRange, ratioBean);
            }
        }

        public String toString() {
            List<String> s = Lists.newArrayList();
            for (Integer ratio : ratioMap.keySet()) {
                s.add(String.format("%d=%.3f", ratio, ratioMap.get(ratio).getRatio()));
            }
            return StringUtils.join(s, ",");
        }
    }

    @Data
    public static class RealOpenVol {
        private String date;
        private double volumn;
        private double avgPrice;
    }

    public static void main(String[] args) throws Exception {
        double exchange = 6.94;
        double init = 10000 / exchange;
        int beforeYear = 2023, afterYear = 2021, afterYear2 = 2022, historyBeforeYear = 2022;
        //        int beforeYear = 2022, afterYear = 2020, afterYear2 = 2021, historyBeforeYear = 2021;
        double capital = init;
        Map<String, StockRatio> originRatioMap = computeHistoricalOverBollingerRatio(historyBeforeYear);
        Set<String> stockSet = Sets.newHashSet("BIOR", "HALL", "OBLG", "ALBT", "IPDN", "OPGN", "TENX", "AYTU", "DAVE", "NXTP", "ATHE", "CANF", "GHSI", "EEMX", "EFAX", "HYMB", "NYC", "SPYX", "PBLA", "JEF", "ACGN", "EAR", "FWBI", "IDRA", "JFU", "CNET", "APM", "JAGX", "OCSL", "OGEN", "SIEN", "SRKZG", "CETX", "UVIX", "EDBL", "PHIO", "SWVL", "MRKR", "REED", "WISA", "FTFT", "FVCB", "LMNL", "REVB", "DYNT", "BRSF", "LCI", "DGLY", "PCAR", "CZOO", "MIGI", "NAOV", "COMS", "GFAI", "INBS", "SNGX", "APRE", "FNGG", "GNUS", "VYNE", "CRBP", "ATNX", "CFRX", "ECOR", "NVDEF", "SHIP", "AMST", "GMBL", "RELI", "WINT", "FNRN", "MFH", "XBRAF", "RKDA", "HCDI", "IONM", "VXX", "SFT", "VEON", "AKAN", "NYMT", "ORTX", "ASLN", "KRBP", "IVOG", "IVOO", "IVOV", "VIOG", "VIOO", "VIOV", "GRAY", "MRBK", "BAOS", "GGB", "LKCO", "TESTING", "VIA", "IDAI", "PTIX", "RDHL", "CUEN", "FRGT", "GCBC", "ALLR", "CREX", "MTP", "MNST", "NOGN", "BPTS", "CETXP", "ENSC", "HLBZ", "CHNR", "BEST", "MBIO", "WTER", "AGRX", "BLBX", "VBIV", "WISH", "EJH", "ARVL", "MEIP", "MINM", "ASNS", "VERB", "BKTI", "FRSX", "OIG", "LGMK", "POAI", "SMFL", "CLXT", "JXJT", "SBET", "EZFL", "IMPP", "MEME", "PSTV", "VISL", "WEED", "MDRR", "MULN", "WGS", "GTE", "SMH", "CRESY", "BBIG", "HEPA", "AWH", "FRLN", "LPCN", "RETO", "VERO", "ALPP", "BNMV", "EAST", "GLMD", "IFBD", "RETO", "XBIO", "XELA", "XELAP", "CYCN", "GREE", "SDIG", "BIOC", "AULT", "NISN", "CHDN", "LGMK", "HLBZ", "LPCN", "BBIG", "XBIO", "JATT", "TGAA", "GRAY", "GREE", "SDIG", "SMFL", "SMFG", "VERO", "LCI", "TYDE", "DRMA", "BLIN", "HEPA", "SESN", "CR", "LITM", "SNGX", "GE", "MULN", "CGNX", "ML", "MDRR", "PR", "VAL", "EBF", "MTP", "CYCN", "XELA", "ENVX", "EQT", "GLMD", "DCFC", "POAI", "BNOX", "FRLN", "CINC", "NISN", "REFR", "CAPR", "SYRS", "ALPP", "RETO", "VISL", "GNLN", "JXJT", "SAFE", "EZFL", "IDRA", "CRESY", "IMPP", "ZEV", "EAST", "BIOC", "IFBD", "STAR", "AWH", "TNXP", "WORX", "VLON", "PSTV", "SFT", "AGRX", "MBIO", "APRE", "GAME", "VERB", "CFRX", "BLBX", "COMS", "RKDA", "WISH", "NXTP", "TR", "ARVL", "EJH", "MEIP", "ENSC", "NYMT", "PNTM", "ASNS", "AKAN", "RDFN", "GMBL", "VYNE", "MNST", "LCAA", "FRSX", "CRBP", "ATNX", "OIG", "REED", "OUST", "ALLR", "NAOV", "KRBP", "ICMB", "XOS", "GFAI", "GNUS", "BGXX", "FTFT", "AMST", "FCUV", "VBIV", "BIIB", "MINM", "CLXT", "DGLY", "MRKR");
        stockSet.forEach(s -> originRatioMap.remove(s));
        BaseUtils.filterStock(originRatioMap.keySet());

        Map<String, String> dailyFileMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "merge");

        // 构建2023年各股票k线
        Map<String, Map<String, StockKLine>> dateToStockLineMap = Maps.newHashMap();
        for (String stock : originRatioMap.keySet()) {
            if (StringUtils.isNotBlank(TEST_STOCK) && !stock.equals(TEST_STOCK)) {
                continue;
            }
            String filePath = dailyFileMap.get(stock);
            List<StockKLine> kLines = BaseUtils.loadDataToKline(filePath, beforeYear, afterYear);

            for (StockKLine kLine : kLines) {
                String date = kLine.getDate();
                if (!dateToStockLineMap.containsKey(date)) {
                    dateToStockLineMap.put(date, Maps.newHashMap());
                }
                dateToStockLineMap.get(date).put(stock, kLine);
            }
        }

        // 构建2023年各股票bolling线
        Map<String, Map<String, BOLL>> dateToStockBollMap = Maps.newHashMap();
        for (String stock : originRatioMap.keySet()) {
            if (StringUtils.isNotBlank(TEST_STOCK) && !stock.equals(TEST_STOCK)) {
                continue;
            }
            List<BOLL> bolls = BaseUtils.readBollFile(Constants.HIS_BASE_PATH + "mergeBoll/" + stock, beforeYear, afterYear2);

            for (BOLL boll : bolls) {
                String date = boll.getDate();
                if (!dateToStockBollMap.containsKey(date)) {
                    dateToStockBollMap.put(date, Maps.newHashMap());
                }
                dateToStockBollMap.get(date).put(stock, boll);
            }
        }

        // 准备当天和20天前的日期映射，用于实时计算布林值
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
        //        dateList = dateList.subList(0, 75);

        // 加载开盘有真实交易的股票
        Map<String, Map<String, SimpleTrade>> dateToOpenTradeMap = Maps.newHashMap();
        Map<String, String> openFirstFileMap = BaseUtils.getFileMap(Constants.TRADE_PATH + "openFirstTrade");
        for (String stock : openFirstFileMap.keySet()) {
            List<String> lines = BaseUtils.readFile(openFirstFileMap.get(stock));
            if (CollectionUtils.isEmpty(lines)) {
                continue;
            }

            lines.remove(lines.size() - 1);
            for (String line : lines) {
                String[] split = line.split(",");
                if (split.length < 3) {
                    continue;
                }
                String date = split[0];
                double price = Double.parseDouble(split[1]);
                String tradeTime = split[2];
                String[] timeSplit = tradeTime.split(":");
                String secondStr = timeSplit[2];
                int second = Integer.valueOf(secondStr.substring(0, 2));
                int minute = Integer.valueOf(timeSplit[1]);
                if (minute > 30 || second > 5) {
                    continue;
                }

                if (!dateToOpenTradeMap.containsKey(date)) {
                    dateToOpenTradeMap.put(date, Maps.newHashMap());
                }
                SimpleTrade openTrade = new SimpleTrade();
                openTrade.setCode(stock);
                openTrade.setDate(date);
                openTrade.setTradePrice(price);

                dateToOpenTradeMap.get(date).put(stock, openTrade);
            }
        }

        // 用开盘价真实价计算boll然后倒排diff
        Map<String, List<Map.Entry<String, Double>>> dateToStocksMap = Maps.newHashMap();
        for (String date : dateToStockBollMap.keySet()) {
            Map<String, SimpleTrade> stockToOpenTradeMap = dateToOpenTradeMap.get(date);
            if (stockToOpenTradeMap == null) {
                continue;
            }

            Map<String, StockKLine> stockKLineMap = dateToStockLineMap.get(date);
            Map<String, BOLL> stockToBollMap = dateToStockBollMap.get(date);
            Map<String, Double> stockToRatioMap = Maps.newHashMap();
            for (String stock : stockToOpenTradeMap.keySet()) {
                if (date.equals("06/07/2023") && StringUtils.equalsAny(stock, "CVGW", "HCP", "UNFI", "NVCR")) {
                    System.out.println();
                }
                SimpleTrade openTrade = stockToOpenTradeMap.get(stock);
                if (openTrade == null) {
                    continue;
                }
                BOLL boll = stockToBollMap.get(stock);
                if (boll == null) {
                    continue;
                }

                StockKLine kLine = stockKLineMap.get(stock);
                if (kLine == null) {
                    continue;
                }

                double open = kLine.getOpen();
                double currMb = boll.getMb();

                if (open > currMb) {
                    continue;
                }

                // 根据开盘价实时算布林上轨
                BigDecimal m20close = BigDecimal.valueOf(open);
                List<String> _20day = dateToBefore20dayMap.get(date);
                List<StockKLine> _20Kline = Lists.newArrayList(kLine);
                boolean failed = false;
                for (int k = 0; k < _20day.size(); k++) {
                    String day = _20day.get(k);
                    StockKLine temp = dateToStockLineMap.get(day).get(stock);
                    if (temp == null) {
                        failed = true;
                        break;
                    }
                    _20Kline.add(temp);
                    m20close = m20close.add(BigDecimal.valueOf(temp.getClose()));
                }
                if (failed) {
                    continue;
                }

                double mb = m20close.divide(BigDecimal.valueOf(20), 2, ROUND_HALF_UP).doubleValue();
                BigDecimal avgDiffSum = BigDecimal.ZERO;
                for (int k = 0; k < _20Kline.size(); k++) {
                    StockKLine temp = _20Kline.get(k);
                    double c = temp.getClose();
                    if (k == 0) {
                        c = open;
                    }
                    avgDiffSum = avgDiffSum.add(BigDecimal.valueOf(c - mb).pow(2));
                }

                double md = Math.sqrt(avgDiffSum.doubleValue() / 20);
                BigDecimal mdPow2 = BigDecimal.valueOf(md).multiply(BigDecimal.valueOf(2));
                double dn = BigDecimal.valueOf(mb).subtract(mdPow2).setScale(3, ROUND_DOWN).doubleValue();
                if (dn > 0) {
                    double ratio = (dn - open) / dn * 100;
                    if (ratio < 0) {
                        continue;
                    }
                    stockToRatioMap.put(stock, ratio);
                }
            }
            List<Map.Entry<String, Double>> stocks = stockToRatioMap.entrySet().stream().sorted((o1, o2) -> {
                if (o1.getValue() < o2.getValue()) {
                    return 1;
                }
                return -1;
            }).collect(Collectors.toList());
            dateToStocksMap.put(date, stocks);
        }

        // 加载2023年每支股票的真实开盘交易量和均价
        Map<String, Map<String, RealOpenVol>> dateToStockRealOpenVolMap = Maps.newHashMap();
        for (String stock : originRatioMap.keySet()) {
            if (StringUtils.isNotBlank(TEST_STOCK) && !stock.equals(TEST_STOCK)) {
                continue;
            }
            List<String> lineList = BaseUtils.readFile(Constants.TRADE_OPEN_PATH + beforeYear + "/" + stock);
            for (String line : lineList) {
                String[] split = line.split(",");
                String date = split[0];
                String volumn = split[1];
                String avgPrice = split[2];
                if (volumn.equals("0") || StringUtils.isBlank(volumn)) {
                    continue;
                }

                if (!dateToStockRealOpenVolMap.containsKey(date)) {
                    dateToStockRealOpenVolMap.put(date, Maps.newHashMap());
                }
                RealOpenVol realOpenVol = new RealOpenVol();
                realOpenVol.setDate(date);
                realOpenVol.setVolumn(Double.valueOf(volumn));
                realOpenVol.setAvgPrice(Double.valueOf(avgPrice));
                dateToStockRealOpenVolMap.get(date).put(stock, realOpenVol);
            }
        }

        List<Double> hitRatio = Lists.newArrayList(0.5d, 0.6d, 0.7d, 0.8d, 0.9d);
        List<Double> lossRatioRange = Lists.newArrayList(0.07d, 0.08d, 0.09d, 0.1d, 0.2d, 0.3d);
        List<Integer> openRange = Lists.newArrayList(6, 7);
        for (Integer openR : openRange) {
            for (Double lossRange : lossRatioRange) {
                for (int i = 0; i < hitRatio.size(); i++) {
                    double hit = hitRatio.get(i);

                    //                Double nextHit = 2d;
                    //                if (i + 1 < hitRatio.size()) {
                    //                    nextHit = hitRatio.get(i + 1);
                    //                }
                    if (hit != 0.7d || lossRange != 0.07d || openR != 6) {
                                                continue;
                    }
                    Map<String, StockRatio> ratioMap = SerializationUtils.clone((HashMap<String, StockRatio>) originRatioMap);

                    int gainCount = 0, lossCount = 0;
                    for (int j = 0; j < dateList.size(); j++) {
                        String date = dateList.get(j);
                        Map<String, BOLL> lastStockBollMap = Maps.newHashMap();
                        String lastDate = "";
                        if (j > 0) {
                            lastDate = dateList.get(j - 1);
                            lastStockBollMap = dateToStockBollMap.get(lastDate);
                        }
                        Map<String, StockKLine> stockKLineMap = dateToStockLineMap.get(date);
                        Map<String, BOLL> stockBollMap = dateToStockBollMap.get(date);
                        List<Map.Entry<String, Double>> stocksEntry = dateToStocksMap.get(date);
                        if (CollectionUtils.isEmpty(stocksEntry)) {
                            continue;
                        }
                        Map<String, RealOpenVol> stockRealOpenVolMap = dateToStockRealOpenVolMap.get(date);

                        boolean hasCompute = false;
                        double income = 0;
                        double sum = capital;
                        int size = 0;
                        for (Map.Entry<String, Double> stockEntry : stocksEntry) {
                            String stock = stockEntry.getKey();
                            StockKLine kLine = stockKLineMap.get(stock);
                            BOLL boll = stockBollMap.get(stock);
//                            BOLL lastBoll = lastStockBollMap.get(lastDate);
//
                            double open = kLine.getOpen();
                            double close = kLine.getClose();
                            double low = kLine.getLow();
//                            double currMb = boll.getMb();
//                            if (lastBoll != null) {
//                                double lastDn = lastBoll.getDn();
//                                if (open > lastDn) {
//                                    continue;
//                                }
//                            }
//
                            if (open < openR) {
                                continue;
                            }
//
//                            if (open > currMb) {
//                                continue;
//                            }
//
//                            // 根据开盘价实时算布林上轨
//                            BigDecimal m20close = BigDecimal.valueOf(open);
//                            List<String> _20day = dateToBefore20dayMap.get(date);
//                            List<StockKLine> _20Kline = Lists.newArrayList(kLine);
//                            boolean failed = false;
//                            for (int k = 0; k < _20day.size(); k++) {
//                                String day = _20day.get(k);
//                                StockKLine temp = dateToStockLineMap.get(day).get(stock);
//                                if (temp == null) {
//                                    failed = true;
//                                    break;
//                                }
//                                _20Kline.add(temp);
//                                m20close = m20close.add(BigDecimal.valueOf(temp.getClose()));
//                            }
//                            if (failed) {
//                                continue;
//                            }
//
//                            double mb = m20close.divide(BigDecimal.valueOf(20), 2, ROUND_HALF_UP).doubleValue();
//                            BigDecimal avgDiffSum = BigDecimal.ZERO;
//                            for (int k = 0; k < _20Kline.size(); k++) {
//                                StockKLine temp = _20Kline.get(k);
//                                double c = temp.getClose();
//                                if (k == 0) {
//                                    c = temp.getOpen();
//                                }
//                                avgDiffSum = avgDiffSum.add(BigDecimal.valueOf(c - mb).pow(2));
//                            }
//
//                            double md = Math.sqrt(avgDiffSum.doubleValue() / 20);
//                            BigDecimal mdPow2 = BigDecimal.valueOf(md).multiply(BigDecimal.valueOf(2));
//                            double dn = BigDecimal.valueOf(mb).subtract(mdPow2).setScale(3, ROUND_DOWN).doubleValue();
//
//                            if (open > dn) {
//                                continue;
//                            }
                            // 根据开盘价算openDnDiffRatio
//                            double openDnDiffPnt = (dn - open) / dn;
                            double openDnDiffPnt = stockEntry.getValue();
                            int openDnDiffInt = (int) openDnDiffPnt;
                            BigDecimal volume = kLine.getVolume();
                            int avgVolume = (int) volume.doubleValue() / 360;

                            StockRatio stockRatio = ratioMap.get(stock);
                            Map<Integer, RatioBean> ratioDetail = stockRatio.getRatioMap();
                            if (MapUtils.isEmpty(ratioDetail)) {
                                stockRatio.addBean(buildBean(kLine, boll));
                                continue;
                            }

                            if (openDnDiffInt>6) {
                                openDnDiffInt = 6;
                            }
                            RatioBean ratioBean = ratioDetail.get(openDnDiffInt);
                            if (ratioBean == null || ratioBean.getRatio() < hit || ratioBean.beanList.size() <= 1) {
                                stockRatio.addBean(buildBean(kLine, boll));
                                continue;
                            }

                            if (hasCompute) {
                                stockRatio.addBean(buildBean(kLine, boll));
                                continue;
                            }

                            int count = (int) (sum / open);
                            if (stockRealOpenVolMap == null) {
                                System.out.println();
                            }
                            RealOpenVol realOpenVol = stockRealOpenVolMap.get(stock);
                            if (realOpenVol == null) {
                                continue;
                            }
                            avgVolume = (int) realOpenVol.getVolumn() / 2;
                            if (count == 0) {
                                break;
                            }
                            double lossRatio = (open - low) / open;
                            double v = lossRange;
                            if (avgVolume < count) {
                                count = avgVolume;
                            }
                            sum -= count * open;
                            if (lossRatio > v) {
                                double loss = -count * open * v;
                                income += loss;
//                                if (j > dateList.size() - 10) {
                                    System.out.println("date=" + date + ", stock=" + stock + ", open=" + open + ", close=" + close + ", volumn=" + volume + ", count=" + count + ", loss = " + (int) loss * exchange);
//                                }
                                //                                                        System.out.println(String.format("loss lossRatio=%d", (int)(lossRatio*100)));
                                //                            stockRatio.addBean(buildBean(kLine, boll));
                                lossCount++;
                                //                            break;
                            } else {
                                double gain = count * (close - open);
                                income += gain;
//                                if (j > dateList.size() - 10) {
                                    System.out.println("date=" + date + ", stock=" + stock + ", open=" + open + ", close=" + close + ", volumn=" + volume + ", count=" + count + ", gain = " + (int) gain * exchange);
//                                }
                                //                            stockRatio.addBean(buildBean(kLine, boll));

                                if (gain >= 0) {
                                    //                                System.out.println(String.format("gain openLowDiff=%d", openLowDiff));
                                    gainCount++;
                                } else {
                                    lossCount++;
                                    //                                System.out.println(String.format("loss openLowDiff=%d, closeOpenDiff=%d", openLowDiff, (int) ((close - open) / open * 100)));
                                }
                                //                            break;
                            }
                            stockRatio.addBean(buildBean(kLine, boll));
                            size++;
                        }
                        capital += income;
                        //                        System.out.println("date=" + date + ", income=" + income + ", capital=" + capital * exchange);
                        //                        System.out.println(date+" "+size);
                    }
                    double successRatio = (double) gainCount / (gainCount + lossCount);
                    System.out.println("openRange=" + openR + ", hit=" + hit + ", loss=" + lossRange + ", sum=" + (int) (capital * exchange) + ", gainCount=" + gainCount + ", lossCount=" + lossCount + ", successRatio=" + successRatio);
                    capital = init;
                }
                System.out.println();
            }
        }
    }

    public static Map<String, StockRatio> computeHistoricalOverBollingerRatio(int beforeYear) throws Exception {
        String mergePath = Constants.HIS_BASE_PATH + "merge/";
        Map<String, String> dailyFileMap = BaseUtils.getFileMap(mergePath);
        List<String> filterStock = FilterStock.tradeFlat(mergePath);

        Map<String, StockRatio> stockRatioMap = Maps.newHashMap();
        for (String stock : dailyFileMap.keySet()) {
            if (StringUtils.isNotBlank(TEST_STOCK) && !stock.equals(TEST_STOCK)) {
                continue;
            }
            if (!filterStock.contains(stock)) {
                //                continue;
            }
            if (SKIP_SET.contains(stock)) {
                continue;
            }

            String filePath = dailyFileMap.get(stock);
            List<StockKLine> kLines = BaseUtils.loadDataToKline(filePath, 2022, 0);
            Map<String, StockKLine> dateToKLineMap = kLines.stream().collect(Collectors.toMap(StockKLine::getDate, k -> k, (k1, k2) -> k1));

            List<BOLL> bolls = BaseUtils.readBollFile(Constants.HIS_BASE_PATH + "bollWithOpen/" + stock, 2023, 0);
            Map<String, BOLL> dateToBollMap = bolls.stream().collect(Collectors.toMap(BOLL::getDate, b -> b, (b1, b2) -> b1));

            List<Bean> result = strategy1(dateToKLineMap, dateToBollMap);

            StockRatio stockRatio = new StockRatio();
            result.stream().forEach(r -> stockRatio.addBean(r));
            stockRatioMap.put(stock, stockRatio);
        }

        //        for (String stock : stockRatioMap.keySet()) {
        //            StockRatio ratio = stockRatioMap.get(stock);
        //            if (MapUtils.isEmpty(ratio.getRatioMap())) {
        //                continue;
        //            }
        //            System.out.println(stock + ": " + ratio);
        //        }

        return stockRatioMap;
    }

    private static List<Bean> strategy1(Map<String, StockKLine> dateToKLineMap, Map<String, BOLL> dateToBollMap) {
        List<Bean> result = Lists.newArrayList();
        for (String date : dateToKLineMap.keySet()) {
            if (!dateToBollMap.containsKey(date)) {
                continue;
            }

            BOLL boll = dateToBollMap.get(date);
            double dn = boll.getDn();
            if (dn == 0) {
                continue;
            }

            StockKLine kLine = dateToKLineMap.get(date);
            double open = kLine.getOpen();
            double low = kLine.getLow();
            if (low < dn && open < dn) {
                result.add(buildBean(kLine, boll));
            }
        }
        Collections.sort(result, ((Comparator<Bean>) (o1, o2) -> BaseUtils.dateToInt(o1.getDate()) - BaseUtils.dateToInt(o2.getDate())).reversed());
        return result;
    }

    private static Bean buildBean(StockKLine kLine, BOLL boll) {
        double dn = boll.getDn();
        String date = kLine.getDate();
        double high = kLine.getHigh();
        double close = kLine.getClose();
        double open = kLine.getOpen();
        double low = kLine.getLow();

        Bean bean = new Bean();
        bean.setDate(date);
        bean.setOpen(open);
        bean.setClose(close);
        bean.setHigh(high);
        bean.setLow(low);
        bean.setDn(dn);

        double lowDnDiffPnt = BigDecimal.valueOf((dn - low) / dn).setScale(4, ROUND_DOWN).multiply(BigDecimal.valueOf(100)).doubleValue();
        bean.setLowDnDiffPnt(lowDnDiffPnt);

        double highCloseDiffPnt = BigDecimal.valueOf((high - close) / close).setScale(4, ROUND_DOWN).multiply(BigDecimal.valueOf(100)).doubleValue();
        bean.setHighCloseDiffPnt(highCloseDiffPnt);

        double closeUpDiffPnt = BigDecimal.valueOf((close - dn) / dn).setScale(4, ROUND_DOWN).multiply(BigDecimal.valueOf(100)).doubleValue();
        bean.setCloseUpDiffPnt(closeUpDiffPnt);

        double openDnDiffPnt = BigDecimal.valueOf((dn - open) / dn).setScale(4, ROUND_DOWN).multiply(BigDecimal.valueOf(100)).doubleValue();
        bean.setOpenDnDiffPnt(openDnDiffPnt);

        bean.setCloseLessOpen(close > open ? 1 : 0);
        return bean;
    }

    private static Map<String, StockKLine> nextCloseLessCurrClose(List<StockKLine> kLineList) throws Exception {
        Map<String, StockKLine> map = Maps.newHashMap();
        for (int i = 0; i < kLineList.size() - 1; i++) {
            StockKLine next = kLineList.get(i);
            StockKLine current = kLineList.get(i + 1);
            String date = current.getDate();

            if (next.getClose() < current.getClose()) {
                map.put(date, current);
            }
        }

        return map;
    }
}
