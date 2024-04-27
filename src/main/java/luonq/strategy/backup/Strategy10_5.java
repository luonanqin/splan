package luonq.strategy.backup;

import bean.BOLL;
import bean.Bean;
import bean.EarningDate;
import bean.RatioBean;
import bean.RealOpenVol;
import bean.SimpleTrade;
import bean.StockKLine;
import bean.StockRatio;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.SerializationUtils;
import org.apache.commons.lang3.StringUtils;
import util.BaseUtils;
import util.Constants;

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
 * 1.计算2021-2022年之间，当日开盘低于实时布林线下轨的比例（dn-open)/dn=>x，及当日收盘大于开盘的成功率=>y
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
 * openRange=7, hit=0.5, loss=0.3, sum=533925, gainCount=159, lossCount=71, successRatio=0.691304347826087
 */
public class Strategy10_5 {

    public static final String TEST_STOCK = "";
    public static final Set<String> SKIP_SET = Sets.newHashSet("FRC", "SIVBQ");

    public static void main(String[] args) throws Exception {
        double exchange = 6.94;
        double init = 10000 / exchange;
        int beforeYear = 2023, afterYear = 2021, afterYear2 = 2022;
        double capital = init;
        Map<String, StockRatio> originRatioMap = computeHistoricalOverBollingerRatio();
        Set<String> invalidStockSet = Sets.newHashSet("FRC", "SIVB", "BIOR", "HALL", "OBLG", "ALBT", "IPDN", "OPGN", "TENX", "AYTU", "DAVE", "NXTP", "ATHE", "CANF", "GHSI", "EEMX", "EFAX", "HYMB", "NYC", "SPYX", "PBLA", "JEF", "ACGN", "EAR", "FWBI", "IDRA", "JFU", "CNET", "APM", "JAGX", "OCSL", "OGEN", "SIEN", "SRKZG", "CETX", "UVIX", "EDBL", "PHIO", "SWVL", "MRKR", "REED", "WISA", "FTFT", "FVCB", "LMNL", "REVB", "DYNT", "BRSF", "LCI", "DGLY", "PCAR", "CZOO", "MIGI", "NAOV", "COMS", "GFAI", "INBS", "SNGX", "APRE", "FNGG", "GNUS", "VYNE", "CRBP", "ATNX", "CFRX", "ECOR", "NVDEF", "SHIP", "AMST", "GMBL", "RELI", "WINT", "FNRN", "MFH", "XBRAF", "RKDA", "HCDI", "IONM", "VXX", "SFT", "VEON", "AKAN", "NYMT", "ORTX", "ASLN", "KRBP", "IVOG", "IVOO", "IVOV", "VIOG", "VIOO", "VIOV", "GRAY", "MRBK", "BAOS", "GGB", "LKCO", "TESTING", "VIA", "IDAI", "PTIX", "RDHL", "CUEN", "FRGT", "GCBC", "ALLR", "CREX", "MTP", "MNST", "NOGN", "BPTS", "CETXP", "ENSC", "HLBZ", "CHNR", "BEST", "MBIO", "WTER", "AGRX", "BLBX", "VBIV", "WISH", "EJH", "ARVL", "MEIP", "MINM", "ASNS", "VERB", "BKTI", "FRSX", "OIG", "LGMK", "POAI", "SMFL", "CLXT", "JXJT", "SBET", "EZFL", "IMPP", "MEME", "PSTV", "VISL", "WEED", "MDRR", "MULN", "WGS", "GTE", "SMH", "CRESY", "BBIG", "HEPA", "AWH", "FRLN", "LPCN", "RETO", "VERO", "ALPP", "BNMV", "EAST", "GLMD", "IFBD", "RETO", "XBIO", "XELA", "XELAP", "CYCN", "GREE", "SDIG", "BIOC", "AULT", "NISN", "CHDN", "LGMK", "HLBZ", "LPCN", "BBIG", "XBIO", "JATT", "TGAA", "GRAY", "GREE", "SDIG", "SMFL", "SMFG", "VERO", "LCI", "TYDE", "DRMA", "BLIN", "HEPA", "SESN", "CR", "LITM", "SNGX", "GE", "MULN", "CGNX", "ML", "MDRR", "PR", "VAL", "EBF", "MTP", "CYCN", "XELA", "ENVX", "EQT", "GLMD", "DCFC", "POAI", "BNOX", "FRLN", "CINC", "NISN", "REFR", "CAPR", "SYRS", "ALPP", "RETO", "VISL", "GNLN", "JXJT", "SAFE", "EZFL", "IDRA", "CRESY", "IMPP", "ZEV", "EAST", "BIOC", "IFBD", "STAR", "AWH", "TNXP", "WORX", "VLON", "PSTV", "SFT", "AGRX", "MBIO", "APRE", "GAME", "VERB", "CFRX", "BLBX", "COMS", "RKDA", "WISH", "NXTP", "TR", "ARVL", "EJH", "MEIP", "ENSC", "NYMT", "PNTM", "ASNS", "AKAN", "RDFN", "GMBL", "VYNE", "MNST", "LCAA", "FRSX", "CRBP", "ATNX", "OIG", "REED", "OUST", "ALLR", "NAOV", "KRBP", "ICMB", "XOS", "GFAI", "GNUS", "BGXX", "FTFT", "AMST", "FCUV", "VBIV", "BIIB", "MINM", "CLXT", "DGLY", "MRKR");
        invalidStockSet.forEach(s -> originRatioMap.remove(s));
        Set<String> stockSet = originRatioMap.keySet();
        BaseUtils.filterStock(stockSet);

        Map<String, String> dailyFileMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "merge");

        // 构建2023年各股票k线
        Map<String, Map<String, StockKLine>> dateToStockLineMap = Maps.newHashMap();
        for (String stock : stockSet) {
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
        for (String stock : stockSet) {
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
        Map<String, List<EarningDate>> earningDateMap = BaseUtils.getEarningDate(null);
        Map<String, Map<String, Double>> dateToStockRatioMap = Maps.newHashMap();
        for (int j = 0; j < dateList.size(); j++) {
            Map<String, Double> stockToRatioMap = Maps.newHashMap();
            String date = dateList.get(j);
            Map<String, StockKLine> stockKLineMap = dateToStockLineMap.get(date);
            List<EarningDate> earningDates = MapUtils.getObject(earningDateMap, date, Lists.newArrayList());
            Set<String> earningStockSet = earningDates.stream().map(EarningDate::getStock).collect(Collectors.toSet());

            Set<String> lastEarningStockSet = Sets.newHashSet();
            if (j > 0) {
                List<EarningDate> lastEarningDates = MapUtils.getObject(earningDateMap, dateList.get(j - 1), Lists.newArrayList());
                lastEarningStockSet = lastEarningDates.stream().map(EarningDate::getStock).collect(Collectors.toSet());
            }

            for (String stock : stockSet) {
                StockKLine kLine = stockKLineMap.get(stock);
                if (kLine == null) {
                    continue;
                }
                if (earningStockSet.contains(stock) || lastEarningStockSet.contains(stock)) {
                                        continue;
                }
                if (date.equals("08/18/2023") && (stock.equals("RRGB")||stock.equals("MARA"))) {
//                    System.out.println();
                }
                double open = kLine.getOpen();
                BigDecimal m20close = BigDecimal.valueOf(open);
                List<String> _20day = dateToBefore20dayMap.get(date);
                List<Double> _20Kline = Lists.newArrayList(open);
                boolean failed = false;
                for (String day : _20day) {
                    StockKLine temp = dateToStockLineMap.get(day).get(stock);
                    if (temp == null) {
                        failed = true;
                        break;
                    }
                    _20Kline.add(temp.getClose());
                    m20close = m20close.add(BigDecimal.valueOf(temp.getClose()));
                }
                if (failed) {
                    continue;
                }

                double mb = m20close.divide(BigDecimal.valueOf(20), 2, ROUND_HALF_UP).doubleValue();
                BigDecimal avgDiffSum = BigDecimal.ZERO;
                for (Double price : _20Kline) {
                    avgDiffSum = avgDiffSum.add(BigDecimal.valueOf(price - mb).pow(2));
                }

                double md = Math.sqrt(avgDiffSum.doubleValue() / 20);
                BigDecimal mdPow2 = BigDecimal.valueOf(md).multiply(BigDecimal.valueOf(2));
                double dn = BigDecimal.valueOf(mb).subtract(mdPow2).setScale(3, ROUND_DOWN).doubleValue();
                if (dn < 0 || open > dn) {
                    continue;
                }

                double ratio = (dn - open) / dn * 100;
                stockToRatioMap.put(stock, ratio);
            }

            List<String> stocks = stockToRatioMap.entrySet().stream().sorted((o1, o2) -> {
                if (o1.getValue() < o2.getValue()) {
                    return 1;
                }
                return -1;
            }).map(o -> o.getKey()).collect(Collectors.toList());
            dateToStocksMap.put(date, stocks);
            dateToStockRatioMap.put(date, stockToRatioMap);
        }

        // 加载2023年每支股票的开盘交易量和均价
        Map<String, Map<String, RealOpenVol>> dateToStockRealOpenVolMap = Maps.newHashMap();
        for (String stock : stockSet) {
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
                realOpenVol.setVolume(Double.valueOf(volumn));
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
                        openTrade.setVolume(realOpenVol.getVolume());
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

                    if (hit != 0.5d || lossRange != 0.07d || openR != 7) {
//                        continue;
                    }
                    Map<String, StockRatio> ratioMap = SerializationUtils.clone((HashMap<String, StockRatio>) originRatioMap);

                    int gainCount = 0, lossCount = 0;
                    for (int j = 0; j < dateList.size(); j++) {
                        String date = dateList.get(j);
                        if (date.equals("10/16/2023")) {
                            System.out.println();
                        }
                        Map<String, BOLL> lastStockBollMap = Maps.newHashMap();
                        Map<String, StockKLine> lastStockKLineMap = Maps.newHashMap();
                        String lastDate = "";
                        if (j > 0) {
                            lastDate = dateList.get(j - 1);
                            lastStockBollMap = dateToStockBollMap.get(lastDate);
                            lastStockKLineMap = dateToStockLineMap.get(lastDate);
                        }
                        Map<String, StockKLine> stockKLineMap = dateToStockLineMap.get(date);
                        Map<String, BOLL> stockBollMap = dateToStockBollMap.get(date);
                        List<String> stocks = dateToStocksMap.get(date);
                        Map<String, SimpleTrade> stockRealOpenVolMap = dateToOpenTradeMap.get(date);
                        Map<String, Double> stockToRatioMap = dateToStockRatioMap.get(date);

                        boolean hasCompute = false;
                        double income = 0;
                        double sum = capital;
                        int size = 0;
                        for (String stock : stocks) {
                            StockKLine kLine = stockKLineMap.get(stock);
                            StockKLine lastKLine = lastStockKLineMap.get(stock);
                            BOLL boll = stockBollMap.get(stock);
                            BOLL lastBoll = lastStockBollMap.get(stock);

                            if (lastKLine != null && (lastKLine.getClose()>lastKLine.getOpen() || lastKLine.getVolume().doubleValue() < 100000)) {
                                                                continue;
                            }

                            double open = kLine.getOpen();
                            double close = kLine.getClose();
                            double low = kLine.getLow();
                            if (boll == null) {
                                System.out.println(date+" "+stock+" boll is null");
                                continue;
                            }
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
                            //                            BigDecimal m20close = BigDecimal.valueOf(open);
                            //                            List<String> _20day = dateToBefore20dayMap.get(date);
                            //                            List<StockKLine> _20Kline = Lists.newArrayList(kLine);
                            //                            boolean failed = false;
                            //                            for (String day : _20day) {
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
                            //                            for (StockKLine temp : _20Kline) {
                            //                                double c = temp.getClose();
                            //                                avgDiffSum = avgDiffSum.add(BigDecimal.valueOf(c - mb).pow(2));
                            //                            }
                            //
                            //                            double md = Math.sqrt(avgDiffSum.doubleValue() / 20);
                            //                            BigDecimal mdPow2 = BigDecimal.valueOf(md).multiply(BigDecimal.valueOf(2));
                            //                            double dn = BigDecimal.valueOf(mb).subtract(mdPow2).setScale(3, ROUND_DOWN).doubleValue();

                            // 根据开盘价算openDnDiffRatio
                            double openDnDiffPnt = stockToRatioMap.get(stock);
                            int openDnDiffInt = (int) openDnDiffPnt;
//                            if (openDnDiffInt > 6 && openDnDiffInt < 10) {
//                                openDnDiffInt = 6;
//                            } else if (openDnDiffInt > 10) {
//                                openDnDiffInt = 10;
//                            }
                            if (openDnDiffInt > 6) {
                                openDnDiffInt = 6;
                            }

                            BigDecimal volume = kLine.getVolume();
                            int avgVolume = (int) volume.doubleValue() / 360;

                            StockRatio stockRatio = ratioMap.get(stock);
                            Map<Integer, RatioBean> ratioDetail = stockRatio.getRatioMap();
                            if (MapUtils.isEmpty(ratioDetail)) {
//                                stockRatio.addBean(buildBean(kLine, boll));
                                continue;
                            }

                            RatioBean ratioBean = ratioDetail.get(openDnDiffInt);
                            if (ratioBean == null || ratioBean.getRatio() < hit) {
//                                stockRatio.addBean(buildBean(kLine, boll));
                                continue;
                            }

                            if (hasCompute) {
//                                stockRatio.addBean(buildBean(kLine, boll));
                                continue;
                            }

                            int count = (int) (sum / open);
                            if (stockRealOpenVolMap == null) {
                                continue;
                            }
                            SimpleTrade realOpenVol = stockRealOpenVolMap.get(stock);
                            if (realOpenVol == null || realOpenVol.getVolume() == 0) {
//                                continue;
                            }
//                            avgVolume = (int) realOpenVol.getVolumn() / 2;
                            if (count == 0) {
                                break;
                            }
                            double lossRatio = (open - low) / open;
                            double v = lossRange;
                            if (avgVolume < count) {
//                                count = avgVolume;
                            }
                            sum -= count * open;
                            if (lossRatio > v) {
                                double loss = -count * open * v;
                                income += loss;
//                                System.out.println("date=" + date + ", stock=" + stock + ", open=" + open + ", close=" + close + ", volumn=" + volume + ", count=" + count + ", loss = " + (int) loss * exchange);
                                //                                                        System.out.println(String.format("loss lossRatio=%d", (int)(lossRatio*100)));
                                lossCount++;
                            } else {
                                double gain = count * (close - open);
                                income += gain;
//                                System.out.println("date=" + date + ", stock=" + stock + ", open=" + open + ", close=" + close + ", volumn=" + volume + ", count=" + count + ", gain = " + (int) gain * exchange);

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
//            Map<String, StockKLine> dateToKLineMap = kLines.stream().collect(Collectors.toMap(StockKLine::getDate, k -> k, (k1, k2) -> k1));

//            List<BOLL> bolls = BaseUtils.readBollFile(Constants.HIS_BASE_PATH + "mergeBoll/" + stock, 2022, 2020);
//            Map<String, BOLL> dateToBollMap = bolls.stream().collect(Collectors.toMap(BOLL::getDate, b -> b, (b1, b2) -> b1));

//            List<Bean> result = strategy1(dateToKLineMap, dateToBollMap);
//                        List<Bean> result = strategy(kLines);

            List<BOLL> bollWithOpen = BaseUtils.readBollFile(Constants.HIS_BASE_PATH + "bollWithOpen/" + stock, 2022, 2020);
            Map<String, BOLL> dateToOpenBollMap = bollWithOpen.stream().collect(Collectors.toMap(BOLL::getDate, b -> b, (b1, b2) -> b1));
            List<Bean> result = strategy2(kLines, dateToOpenBollMap);

            StockRatio stockRatio = new StockRatio();
            result.stream().forEach(r -> stockRatio.addBean(r));
            stockRatioMap.put(stock, stockRatio);
        }

        return stockRatioMap;
    }

    private static List<Bean> strategy(List<StockKLine> stockKLines) {
        List<Bean> result = Lists.newArrayList();
        BigDecimal m20close = BigDecimal.ZERO;
        int ma20count = 0;
        double md = 0, mb = 0, dn = 0;
        for (int i = stockKLines.size() - 1; i >= 0; i--) {
            StockKLine kLine = stockKLines.get(i);

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
                dn = BigDecimal.valueOf(mb).subtract(mdPow2).setScale(3, ROUND_DOWN).doubleValue();

                ma20count--;
                m20close = m20close.subtract(BigDecimal.valueOf(stockKLines.get(i + 20 - 1).getClose()));
                m20close = m20close.subtract(open).add(close);

                double low = kLine.getLow();
                if (low < dn && kLine.getOpen() < dn) {
                    result.add(buildBean(kLine, dn));
                }
            }
            if (md == 0) {
                continue;
            }
        }

        return result;
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

    private static List<Bean> strategy2(List<StockKLine> stockKLines, Map<String, BOLL> bollWithOpen) {
        List<Bean> result = Lists.newArrayList();
        for (int i = 0; i < stockKLines.size(); i++) {
            StockKLine kLine = stockKLines.get(i);
            String date = kLine.getDate();
            BOLL boll = bollWithOpen.get(date);
            if (boll == null) {
                continue;
            }

            double dn = boll.getDn();
            double open = kLine.getOpen();
            double low = kLine.getLow();
            if (low < dn && open < dn) {
                result.add(buildBean(kLine, boll));
            }
        }
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

        bean.setCloseGreatOpen(close > open ? 1 : 0);
        return bean;
    }

    private static Bean buildBean(StockKLine kLine, double dn) {
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

        bean.setCloseGreatOpen(close > open ? 1 : 0);
        return bean;
    }
}
