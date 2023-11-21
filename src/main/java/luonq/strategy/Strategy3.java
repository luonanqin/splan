package luonq.strategy;

import bean.BOLL;
import bean.FrontReinstatement;
import bean.MA;
import bean.SimpleTrade;
import bean.SplitStockInfo;
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
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static java.math.BigDecimal.ROUND_DOWN;

/**
 * 1.计算2022年之前，前一日收盘低于布林线下轨的比例（dn-close)/dn=>x，及第二天收盘大于开盘的成功率=>y
 * 2.x的百分比从0~6（大于6的统一为6）作为key，y作为value建立历史策略数据map
 * 3.计算2023年的数据，计算上面的x，取对应的y，与给定的hit进行比较
 * 4.指定lossRange，作为止损线
 * 5.候选需要计算的股票，以前一日的x倒排，按照以下条件进行过滤计算
 *
 * 不满足：
 *   2.如果y比给定的hit小，则不满足条件
 *   4.如果第二天开盘价低于给定openRange，则不满足条件
 *   5.前一日如果整根k线穿过2条及2条以上均线，则不满足条件
 *
 * 不满足的数据，会继续加入历史策略数据map，共后续的计算使用
 * 满足的数据，根据止损线（若触发）进行收益计算
 *
 * 注：参与2023年计算的股票，一定要在开盘后5秒内有真实交易，否则会被过滤
 *
 * 结果：截止六月底
 * openRange=7, hit=0.8, loss=0.09, sum=38729, gainCount=90, lossCount=67, successRatio=0.5732484076433121
 */
public class Strategy3 {

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
        private double closeDnDiffPnt;
        private int nextCloseGtOpen; // true=1 false=0

        public String toString() {
            return String.format("%s\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f\t%.2f", date, open, close, high, low, dn, closeDnDiffPnt);
        }
    }

    @Data
    public static class RatioBean implements Serializable {
        List<Bean> beanList = Lists.newArrayList();
        double ratio;

        public void add(Bean bean) {
            beanList.add(bean);
            long trueCount = beanList.stream().filter(c -> c.getNextCloseGtOpen() == 1).count();
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
            if (low > dn || open > dn) {
                return;
            }

            double lastDayCloseDnDiffPnt = bean.getCloseDnDiffPnt();
            int lastDayCloseDnDiffRange = (int) lastDayCloseDnDiffPnt;
            if (lastDayCloseDnDiffRange < 0) {
                return;
            }
            if (lastDayCloseDnDiffRange > 6) {
                if (!ratioMap.containsKey(6)) {
                    ratioMap.put(6, new RatioBean());
                }
                ratioMap.get(6).add(bean);
            } else if (ratioMap.containsKey(lastDayCloseDnDiffRange)) {
                ratioMap.get(lastDayCloseDnDiffRange).add(bean);
            } else {
                RatioBean ratioBean = new RatioBean();
                ratioBean.add(bean);
                ratioMap.put(lastDayCloseDnDiffRange, ratioBean);
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
        double capital = init;
        Map<String, StockRatio> originRatioMap = getHistoricalOverBollingerRatio(historyBeforeYear);
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
        List<String> dateList = Lists.newArrayList();
        for (int i = 0; i < kLines.size(); i++) {
            if (i + 20 > kLines.size() - 1) {
                break;
            }
            String date = kLines.get(i).getDate();

            String year = date.substring(date.lastIndexOf("/") + 1);
            if (year.equals(String.valueOf(beforeYear))) {
                dateList.add(date);
            }
        }
        Collections.reverse(dateList);

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

        // 加载2023年每支股票的均线
        Map<String, Map<String, MA>> dateToStockMAMap = Maps.newHashMap();
        for (String stock : originRatioMap.keySet()) {
            if (StringUtils.isNotBlank(TEST_STOCK) && !stock.equals(TEST_STOCK)) {
                continue;
            }
            List<String> lineList = BaseUtils.readMaFile(Constants.HIS_BASE_PATH + "mergeMA/" + stock, 2023, 2022);
            for (String line : lineList) {
                MA ma = MA.convert(line);
                String date = ma.getDate();
                if (!dateToStockMAMap.containsKey(date)) {
                    dateToStockMAMap.put(date, Maps.newHashMap());
                }
                dateToStockMAMap.get(date).put(stock, ma);
            }
        }

        // 用收盘价计算ratio然后倒排
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
                if (date.equals("03/06/2023") && StringUtils.equalsAny(stock, "ALHC")) {
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
                double close = kLine.getClose();
                double dn = boll.getDn();

                if (close > dn || dn < 0) {
                    continue;
                }

                double ratio = (dn - close) / dn * 100;
                if (ratio < 0) {
                    continue;
                }
                stockToRatioMap.put(stock, ratio);
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

        // 构建2022年各股票bolling线
        Map<String, Map<String, BOLL>> dateToStockOpenBollMap = Maps.newHashMap();
        for (String stock : originRatioMap.keySet()) {
            if (StringUtils.isNotBlank(TEST_STOCK) && !stock.equals(TEST_STOCK)) {
                continue;
            }
            List<BOLL> bolls = BaseUtils.readBollFile(Constants.HIS_BASE_PATH + "bollWithOpen/" + stock, beforeYear, afterYear2);

            for (BOLL boll : bolls) {
                String date = boll.getDate();
                if (!dateToStockOpenBollMap.containsKey(date)) {
                    dateToStockOpenBollMap.put(date, Maps.newHashMap());
                }
                dateToStockOpenBollMap.get(date).put(stock, boll);
            }
        }

        List<Double> hitRatio = Lists.newArrayList(0.6d, 0.7d, 0.8d, 0.9d, 1d);
        List<Double> lossRatioRange = Lists.newArrayList(0.09d, 0.1d, 0.2d, 0.3d);
        List<Integer> openRange = Lists.newArrayList(6, 7);

        //        openRange.clear();
        //        openRange.add(7);
        //        hitRatio.clear();
        //        hitRatio.add(0.9d);
        //        hitRatio.add(1d);
        for (Integer openR : openRange) {
            for (Double lossRange : lossRatioRange) {
                for (int i = 0; i < hitRatio.size(); i++) {
                    double hit = hitRatio.get(i);

                    //                Double nextHit = 2d;
                    //                if (i + 1 < hitRatio.size()) {
                    //                    nextHit = hitRatio.get(i + 1);
                    //                }
                    if (hit != 0.9d || lossRange != 0.09d || openR != 7) {
//                        continue;
                    }
                    Map<String, StockRatio> ratioMap = SerializationUtils.clone((HashMap<String, StockRatio>) originRatioMap);

                    int gainCount = 0, lossCount = 0;
                    for (int j = 1; j < dateList.size(); j++) {
                        String date = dateList.get(j);
                        if (date.equals("03/07/2023")) {
//                            System.out.println();
                        }
                        String lastDate = dateList.get(j - 1);
                        Map<String, BOLL> lastStockBollMap = dateToStockBollMap.get(lastDate);
                        Map<String, MA> prevStockMAMap = dateToStockMAMap.get(lastDate);
                        Map<String, StockKLine> prevStockKLineMap = dateToStockLineMap.get(lastDate);

                        Map<String, StockKLine> stockKLineMap = dateToStockLineMap.get(date);
                        Map<String, BOLL> stockBollMap = dateToStockBollMap.get(date);
                        List<Map.Entry<String, Double>> stocksEntry = dateToStocksMap.get(lastDate);
                        if (CollectionUtils.isEmpty(stocksEntry)) {
                            continue;
                        }
                        Map<String, RealOpenVol> stockRealOpenVolMap = dateToStockRealOpenVolMap.get(date);
                        Map<String, BOLL> openBollMap = dateToStockOpenBollMap.get(date);

                        double income = 0;
                        double sum = capital;
                        int size = 0;
                        for (Map.Entry<String, Double> stockEntry : stocksEntry) {
                            String stock = stockEntry.getKey();
                            StockKLine kLine = stockKLineMap.get(stock);
                            BOLL boll = stockBollMap.get(stock);
                            BOLL lastBoll = lastStockBollMap.get(stock);
                            BOLL openBoll = openBollMap.get(stock);
                            if (kLine == null || boll == null || lastBoll == null || openBoll == null || prevStockKLineMap == null || prevStockMAMap == null) {
                                continue;
                            }
                            StockKLine prevKLine = prevStockKLineMap.get(stock);
                            MA prevMa = prevStockMAMap.get(stock);

                            double prevHigh = 0, prevLow = 0;
                            List<Double> maList = Lists.newArrayList();
                            if (prevKLine != null && prevMa != null) {
                                prevHigh = prevKLine.getHigh();
                                prevLow = prevKLine.getLow();
                                double ma5 = prevMa.getMa5();
                                double ma10 = prevMa.getMa10();
                                double ma20 = prevMa.getMa20();
                                double ma30 = prevMa.getMa30();
                                double ma60 = prevMa.getMa60();
                                maList.add(ma5);
                                maList.add(ma10);
                                maList.add(ma20);
                                maList.add(ma30);
                                maList.add(ma60);
                            }

                            double open = kLine.getOpen();
                            double close = kLine.getClose();
                            boolean closeGtOpen = close > open;
                            double low = kLine.getLow();
                            if (open < openR) {
                                continue;
                            }

                            // 判断前一日的k线最低到最高中间跨越了多少条均线，超过2条则跳过
                            int overMaTimes = 0;
                            for (Double maN : maList) {
                                if (prevLow < maN && maN < prevHigh) {
                                    overMaTimes++;
                                }
                            }
                            if (overMaTimes >= 2) {
                                //                                                                stockRatio.addBean(buildBean(kLine, boll));
                                continue;
                            }

                            double closeDnDiffPnt = stockEntry.getValue();
                            int closeDnDiffInt = (int) closeDnDiffPnt;

                            StockRatio stockRatio = ratioMap.get(stock);
                            Map<Integer, RatioBean> ratioDetail = stockRatio.getRatioMap();
                            if (MapUtils.isEmpty(ratioDetail)) {
                                stockRatio.addBean(buildBean(kLine, lastBoll, closeGtOpen));
                                continue;
                            }

                            if (closeDnDiffInt > 6) {
                                closeDnDiffInt = 6;
                            }
                            RatioBean ratioBean = ratioDetail.get(closeDnDiffInt);
                            double openBollDn = openBoll.getDn();
                            if (ratioBean == null || ratioBean.getRatio() < hit) {
                                stockRatio.addBean(buildBean(kLine, lastBoll, closeGtOpen));
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
                            int avgVolume = (int) realOpenVol.getVolumn() / 2;
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
                                //                                                                if (j > dateList.size() - 10) {
//                                System.out.println("date=" + date + ", stock=" + stock + ", open=" + open + ", close=" + close + ", count=" + count + ", loss = " + (int) loss * exchange);
                                //                                                                }
                                //                                                        System.out.println(String.format("loss lossRatio=%d", (int)(lossRatio*100)));
                                lossCount++;
                                //                            break;
                            } else {
                                double gain = count * (close - open);
                                income += gain;
                                //                                                                if (j > dateList.size() - 10) {
//                                System.out.println("date=" + date + ", stock=" + stock + ", open=" + open + ", close=" + close + ", count=" + count + ", gain = " + (int) gain * exchange);
                                //                                                                }
                                if (gain >= 0) {
                                    //                                System.out.println(String.format("gain openLowDiff=%d", openLowDiff));
                                    gainCount++;
                                } else {
                                    lossCount++;
                                    //                                System.out.println(String.format("loss openLowDiff=%d, closeOpenDiff=%d", openLowDiff, (int) ((close - open) / open * 100)));
                                }
                            }
                            stockRatio.addBean(buildBean(kLine, lastBoll, closeGtOpen));
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

    public static Map<String, StockRatio> getHistoricalOverBollingerRatio(int beforeYear) throws Exception {
        String mergePath = Constants.HIS_BASE_PATH + "merge/";
        Map<String, String> dailyFileMap = BaseUtils.getFileMap(mergePath);

        // 过滤所有合股
        Set<String> mergeStock = BaseUtils.getMergeStock();

        // 过滤所有拆股
        Set<SplitStockInfo> splitStockInfo = BaseUtils.getSplitStockInfo();
        Set<String> splitStock = splitStockInfo.stream().map(SplitStockInfo::getStock).collect(Collectors.toSet());

        // 过滤所有今年前复权因子低于0.98的
        LocalDate firstDay = LocalDate.parse("2023-01-01", DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        Set<FrontReinstatement> reinstatementInfo = BaseUtils.getFrontReinstatementInfo();
        Map<String, FrontReinstatement> map = reinstatementInfo.stream().collect(Collectors.toMap(FrontReinstatement::getStock, Function.identity()));
        Set<String> frSet = Sets.newHashSet();
        for (String stock : map.keySet()) {
            FrontReinstatement fr = map.get(stock);
            double factor = fr.getFactor();
            if (factor > 0.98) {
                continue;
            }

            String date = fr.getDate();
            LocalDate dateParse = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            if (dateParse.isAfter(firstDay)) {
                frSet.add(stock);
            }
        }
        Map<String, StockRatio> stockRatioMap = Maps.newHashMap();
        for (String stock : dailyFileMap.keySet()) {
            if (StringUtils.isNotBlank(TEST_STOCK) && !stock.equals(TEST_STOCK)) {
                continue;
            }
            if (mergeStock.contains(stock) || splitStock.contains(stock) || frSet.contains(stock)) {
                continue;
            }
            if (SKIP_SET.contains(stock)) {
                continue;
            }

            String filePath = dailyFileMap.get(stock);
            List<StockKLine> kLines = BaseUtils.loadDataToKline(filePath, 2022, 0);
            Map<String, StockKLine> dateToKLineMap = kLines.stream().collect(Collectors.toMap(StockKLine::getDate, k -> k, (k1, k2) -> k1));

            List<BOLL> bolls = BaseUtils.readBollFile(Constants.HIS_BASE_PATH + "mergeBoll/" + stock, 2022, 0);
            Map<String, BOLL> dateToBollMap = bolls.stream().collect(Collectors.toMap(BOLL::getDate, b -> b, (b1, b2) -> b1));

            List<Bean> result = strategy(dateToKLineMap, dateToBollMap);

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

    private static List<Bean> strategy(Map<String, StockKLine> dateToKLineMap, Map<String, BOLL> dateToBollMap) {
        List<Bean> result = Lists.newArrayList();
        List<String> dateList = Lists.newArrayList(dateToKLineMap.keySet());
        Collections.sort(dateList, Comparator.comparingInt(BaseUtils::dateToInt));

        for (int i = 0; i < dateList.size(); i++) {
            if (i == dateList.size() - 1) {
                break;
            }

            String date = dateList.get(i);
            String nextDate = dateList.get(i + 1);
            if (!dateToBollMap.containsKey(date)) {
                continue;
            }

            BOLL boll = dateToBollMap.get(date);
            double dn = boll.getDn();
            if (dn == 0) {
                continue;
            }

            StockKLine kLine = dateToKLineMap.get(date);
            StockKLine nextKLine = dateToKLineMap.get(nextDate);
            double nextOpen = nextKLine.getOpen();
            double nextClose = nextKLine.getClose();
            double open = kLine.getOpen();
            double close = kLine.getClose();
            if (close < dn) {
                result.add(buildBean(kLine, boll, nextClose > nextOpen));
            }
        }
        return result;
    }

    private static Bean buildBean(StockKLine kLine, BOLL boll, boolean nextCloseGtOpen) {
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

        double closeDnDiffPnt = BigDecimal.valueOf((dn - close) / dn).setScale(4, ROUND_DOWN).multiply(BigDecimal.valueOf(100)).doubleValue();
        bean.setCloseDnDiffPnt(closeDnDiffPnt);

        bean.setNextCloseGtOpen(nextCloseGtOpen ? 1 : 0);
        return bean;
    }
}
