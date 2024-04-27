package luonq.strategy;

import bean.BOLL;
import bean.SimpleTrade;
import bean.StockKLine;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.Data;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import util.BaseUtils;
import util.Constants;

import java.io.Serializable;
import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static java.math.BigDecimal.ROUND_DOWN;
import static java.math.BigDecimal.ROUND_HALF_UP;

/**
 * 1.计算2021-2022年之间，当日开盘低于收盘后布林线下轨的比例（dn-open)/dn=>x，及当日收盘大于开盘的成功率=>y
 * 2.x的百分比从0~6（大于6的统一为6）作为key，y作为value建立历史策略数据map
 * 3.计算2023年的数据，计算上面的x，取对应的y，与给定的hit进行比较
 * 4.指定lossRange，作为止损线
 * 5.候选需要计算的股票，以前一日的x倒排，按照以下条件进行过滤计算
 * <p>
 * 不满足：
 * 2.如果y比给定的hit小，则不满足条件
 * 4.如果开盘价低于给定openRange，则不满足条件
 * <p>
 * 不满足的数据，会继续加入历史策略数据map，共后续的计算使用
 * 满足的数据，根据止损线（若触发）进行收益计算
 * <p>
 * 注：参与2023年计算的股票，一定要在开盘后5秒内有真实交易，否则会被过滤
 * <p>
 * 结果：截止8月11日
 * openRange=6, hit=0.9, loss=0.3, sum=1060511, gainCount=184, lossCount=74, successRatio=0.7131782945736435
 */
public class Strategy7 {

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
        int beforeYear = 2023, afterYear = 2021, afterYear2 = 2022;
        double capital = init;
        Map<String, StockRatio> originRatioMap = computeHistoricalOverBollingerRatio();
        Set<String> stockSet = Sets.newHashSet("FRC", "SIVB", "BIOR", "HALL", "OBLG", "ALBT", "IPDN", "OPGN", "TENX", "AYTU", "DAVE", "NXTP", "ATHE", "CANF", "GHSI", "EEMX", "EFAX", "HYMB", "NYC", "SPYX", "PBLA", "JEF", "ACGN", "EAR", "FWBI", "IDRA", "JFU", "CNET", "APM", "JAGX", "OCSL", "OGEN", "SIEN", "SRKZG", "CETX", "UVIX", "EDBL", "PHIO", "SWVL", "MRKR", "REED", "WISA", "FTFT", "FVCB", "LMNL", "REVB", "DYNT", "BRSF", "LCI", "DGLY", "PCAR", "CZOO", "MIGI", "NAOV", "COMS", "GFAI", "INBS", "SNGX", "APRE", "FNGG", "GNUS", "VYNE", "CRBP", "ATNX", "CFRX", "ECOR", "NVDEF", "SHIP", "AMST", "GMBL", "RELI", "WINT", "FNRN", "MFH", "XBRAF", "RKDA", "HCDI", "IONM", "VXX", "SFT", "VEON", "AKAN", "NYMT", "ORTX", "ASLN", "KRBP", "IVOG", "IVOO", "IVOV", "VIOG", "VIOO", "VIOV", "GRAY", "MRBK", "BAOS", "GGB", "LKCO", "TESTING", "VIA", "IDAI", "PTIX", "RDHL", "CUEN", "FRGT", "GCBC", "ALLR", "CREX", "MTP", "MNST", "NOGN", "BPTS", "CETXP", "ENSC", "HLBZ", "CHNR", "BEST", "MBIO", "WTER", "AGRX", "BLBX", "VBIV", "WISH", "EJH", "ARVL", "MEIP", "MINM", "ASNS", "VERB", "BKTI", "FRSX", "OIG", "LGMK", "POAI", "SMFL", "CLXT", "JXJT", "SBET", "EZFL", "IMPP", "MEME", "PSTV", "VISL", "WEED", "MDRR", "MULN", "WGS", "GTE", "SMH", "CRESY", "BBIG", "HEPA", "AWH", "FRLN", "LPCN", "RETO", "VERO", "ALPP", "BNMV", "EAST", "GLMD", "IFBD", "RETO", "XBIO", "XELA", "XELAP", "CYCN", "GREE", "SDIG", "BIOC", "AULT", "NISN", "CHDN", "LGMK", "HLBZ", "LPCN", "BBIG", "XBIO", "JATT", "TGAA", "GRAY", "GREE", "SDIG", "SMFL", "SMFG", "VERO", "LCI", "TYDE", "DRMA", "BLIN", "HEPA", "SESN", "CR", "LITM", "SNGX", "GE", "MULN", "CGNX", "ML", "MDRR", "PR", "VAL", "EBF", "MTP", "CYCN", "XELA", "ENVX", "EQT", "GLMD", "DCFC", "POAI", "BNOX", "FRLN", "CINC", "NISN", "REFR", "CAPR", "SYRS", "ALPP", "RETO", "VISL", "GNLN", "JXJT", "SAFE", "EZFL", "IDRA", "CRESY", "IMPP", "ZEV", "EAST", "BIOC", "IFBD", "STAR", "AWH", "TNXP", "WORX", "VLON", "PSTV", "SFT", "AGRX", "MBIO", "APRE", "GAME", "VERB", "CFRX", "BLBX", "COMS", "RKDA", "WISH", "NXTP", "TR", "ARVL", "EJH", "MEIP", "ENSC", "NYMT", "PNTM", "ASNS", "AKAN", "RDFN", "GMBL", "VYNE", "MNST", "LCAA", "FRSX", "CRBP", "ATNX", "OIG", "REED", "OUST", "ALLR", "NAOV", "KRBP", "ICMB", "XOS", "GFAI", "GNUS", "BGXX", "FTFT", "AMST", "FCUV", "VBIV", "BIIB", "MINM", "CLXT", "DGLY", "MRKR");
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

        // 计算出open低于dn（收盘后的dn）比例最高的前十股票，然后再遍历计算收益
        Map<String, List<String>> dateToStocksMap = Maps.newHashMap();
        for (String date : dateToStockBollMap.keySet()) {
            Map<String, StockKLine> stockToKlineMap = dateToStockLineMap.get(date);
            Map<String, BOLL> stockToBollMap = dateToStockBollMap.get(date);
            Map<String, Double> stockToRatioMap = Maps.newHashMap();
            for (String stock : stockToKlineMap.keySet()) {
                StockKLine kline = stockToKlineMap.get(stock);
                BOLL boll = stockToBollMap.get(stock);
                if (boll != null && boll.getDn() > 0) {
                    double dn = boll.getDn();
                    double open = kline.getOpen();
                    double ratio = (dn - open) / dn;
                    if (ratio < 0) {
                        continue;
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
                //                System.out.println(date + ": " + (stock.size() > 10 ? stock.subList(0, 10) : stock));
            }
        }

        // 加载2023年每支股票的开盘交易量和均价
        Map<String, Map<String, RealOpenVol>> dateToStockRealOpenVolMap = Maps.newHashMap();
        for (String stock : originRatioMap.keySet()) {
            if (StringUtils.isNotBlank(TEST_STOCK) && !stock.equals(TEST_STOCK)) {
                continue;
            }
            List<String> lineList = BaseUtils.readFile(Constants.TRADE_OPEN_PATH + "2023/" + stock);
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
        // 加载开盘有真实交易的股票(5秒内有交易的才算有效开盘)
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

                Map<String, RealOpenVol> realOpenVolMap = dateToStockRealOpenVolMap.get(date);
                if (realOpenVolMap != null) {
                    RealOpenVol realOpenVol = realOpenVolMap.get(stock);
                    if (realOpenVol != null) {
                        openTrade.setVolume(realOpenVol.getVolumn());
                    }
                }

                dateToOpenTradeMap.get(date).put(stock, openTrade);
            }
        }

        List<Double> hitRatio = Lists.newArrayList(0.5d, 0.6d, 0.7d, 0.8d, 0.9d);
        List<Double> lossRatioRange = Lists.newArrayList(0.07d, 0.08d, 0.09d, 0.1d, 0.2d, 0.3d);
        List<Integer> openRange = Lists.newArrayList(6, 7);
        for (Integer openR : openRange) {
            for (Double lossRange : lossRatioRange) {
                for (int i = 0; i < hitRatio.size(); i++) {
                    double hit = hitRatio.get(i);

                    if (hit != 0.5d || lossRange != 0.1d || openR != 6) {
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
                        List<String> stocks = dateToStocksMap.get(date);
                        Map<String, SimpleTrade> stockRealOpenVolMap = dateToOpenTradeMap.get(date);

                        boolean hasCompute = false;
                        double income = 0;
                        double sum = capital;
                        int size = 0;
                        for (String stock : stocks) {
                            StockKLine kLine = stockKLineMap.get(stock);
                            BOLL boll = stockBollMap.get(stock);
                            BOLL lastBoll = lastStockBollMap.get(lastDate);

                            double open = kLine.getOpen();
                            double close = kLine.getClose();
                            double low = kLine.getLow();
                            double currMb = boll.getMb();
                            if (lastBoll != null) {
                                double lastDn = lastBoll.getDn();
                                if (open > lastDn) {
                                    continue;
                                }
                            }

                            if (open < openR) {
                                continue;
                            }

                            if (open > currMb) {
                                continue;
                            }

                            // 根据开盘价实时算布林上轨
                            BigDecimal m20close = BigDecimal.valueOf(open);
                            List<String> _20day = dateToBefore20dayMap.get(date);
                            List<StockKLine> _20Kline = Lists.newArrayList(kLine);
                            boolean failed = false;
                            for (String day : _20day) {
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
                            for (StockKLine temp : _20Kline) {
                                double c = temp.getClose();
                                avgDiffSum = avgDiffSum.add(BigDecimal.valueOf(c - mb).pow(2));
                            }

                            double md = Math.sqrt(avgDiffSum.doubleValue() / 20);
                            BigDecimal mdPow2 = BigDecimal.valueOf(md).multiply(BigDecimal.valueOf(2));
                            double dn = BigDecimal.valueOf(mb).subtract(mdPow2).setScale(3, ROUND_DOWN).doubleValue();

                            if (open > dn) {
                                continue;
                            }
                            // 根据开盘价算openDnDiffRatio
                            double openDnDiffPnt = (dn - open) / dn * 100;
                            int openDnDiffInt = (int) openDnDiffPnt;
                            if (openDnDiffInt > 6) {
                                openDnDiffInt = 6;
                            }
                            BigDecimal volume = kLine.getVolume();
                            int avgVolume = (int) volume.doubleValue() / 360;

                            StockRatio stockRatio = ratioMap.get(stock);
                            Map<Integer, RatioBean> ratioDetail = stockRatio.getRatioMap();
                            if (MapUtils.isEmpty(ratioDetail)) {
                                stockRatio.addBean(buildBean(kLine, boll));
                                continue;
                            }
                            if (date.equals("05/19/2023")) {
                                //                                System.out.println();
                            }
                            RatioBean ratioBean = ratioDetail.get(openDnDiffInt);
                            if (ratioBean == null || ratioBean.getRatio() < hit) {
                                stockRatio.addBean(buildBean(kLine, boll));
                                continue;
                            }

                            if (hasCompute) {
                                stockRatio.addBean(buildBean(kLine, boll));
                                continue;
                            }

                            int count = (int) (sum / open);
                            if (stockRealOpenVolMap == null) {
                                continue;
                            }
                            SimpleTrade realOpenVol = stockRealOpenVolMap.get(stock);
                            if (realOpenVol == null || realOpenVol.getVolume() == 0) {
                                continue;
                            }
                            avgVolume = (int) realOpenVol.getVolume() / 2;
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
                                                                System.out.println("date=" + date + ", stock=" + stock + ", open=" + open + ", close=" + close + ", volumn=" + volume + ", count=" + count + ", loss = " + (int) loss * exchange);
                                //                                                        System.out.println(String.format("loss lossRatio=%d", (int)(lossRatio*100)));
                                lossCount++;
                            } else {
                                double gain = count * (close - open);
                                income += gain;
                                                                System.out.println("date=" + date + ", stock=" + stock + ", open=" + open + ", close=" + close + ", volumn=" + volume + ", count=" + count + ", gain = " + (int) gain * exchange);

                                if (gain >= 0) {
                                    //                                System.out.println(String.format("gain openLowDiff=%d", openLowDiff));
                                    gainCount++;
                                } else {
                                    lossCount++;
                                    //                                System.out.println(String.format("loss openLowDiff=%d, closeOpenDiff=%d", openLowDiff, (int) ((close - open) / open * 100)));
                                }
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

    public static Map<String, StockRatio> computeHistoricalOverBollingerRatio() throws Exception {
        String mergePath = Constants.HIS_BASE_PATH + "merge/";
        Map<String, String> dailyFileMap = BaseUtils.getFileMap(mergePath);

        Map<String, StockRatio> stockRatioMap = Maps.newHashMap();
        for (String stock : dailyFileMap.keySet()) {
            if (StringUtils.isNotBlank(TEST_STOCK) && !stock.equals(TEST_STOCK)) {
                continue;
            }
            if (SKIP_SET.contains(stock)) {
                continue;
            }

            String filePath = dailyFileMap.get(stock);
            List<StockKLine> kLines = BaseUtils.loadDataToKline(filePath, 2022, 2020);
            Map<String, StockKLine> dateToKLineMap = kLines.stream().collect(Collectors.toMap(StockKLine::getDate, k -> k, (k1, k2) -> k1));

            List<BOLL> bolls = BaseUtils.readBollFile(Constants.HIS_BASE_PATH + "mergeBoll/" + stock, 2022, 2020);
            Map<String, BOLL> dateToBollMap = bolls.stream().collect(Collectors.toMap(BOLL::getDate, b -> b, (b1, b2) -> b1));

            List<Bean> result = strategy1(dateToKLineMap, dateToBollMap);

            StockRatio stockRatio = new StockRatio();
            result.stream().forEach(r -> stockRatio.addBean(r));
            stockRatioMap.put(stock, stockRatio);
        }

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
        //        Collections.sort(result, ((Comparator<Bean>) (o1, o2) -> BaseUtils.dateToInt(o1.getDate()) - BaseUtils.dateToInt(o2.getDate())).reversed());
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

        double openDnDiffPnt = BigDecimal.valueOf((dn - open) / dn).setScale(4, ROUND_DOWN).multiply(BigDecimal.valueOf(100)).doubleValue();
        bean.setOpenDnDiffPnt(openDnDiffPnt);

        bean.setCloseLessOpen(close > open ? 1 : 0);
        return bean;
    }
}
