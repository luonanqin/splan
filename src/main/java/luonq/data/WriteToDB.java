package luonq.data;

import bean.BOLL;
import bean.EarningDate;
import bean.MA;
import bean.RealOpenVol;
import bean.SimpleTrade;
import bean.StockKLine;
import bean.StockRehab;
import bean.Total;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import luonq.mapper.EarningDataMapper;
import luonq.mapper.RehabDataMapper;
import luonq.mapper.StockDataMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import util.BaseUtils;
import util.Constants;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import static util.Constants.SPLIT_PATH;

@Component
@Slf4j
public class WriteToDB {

    @Autowired
    private StockDataMapper stockDataMapper;

    @Autowired
    private EarningDataMapper earningDataMapper;

    @Autowired
    private RehabDataMapper rehabDataMapper;

    /**
     * 一次性导入历史数据
     */
    public void importToDB(String stockCode, String date) throws Exception {
        Map<String, String> dailyFileMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "merge/");
        Map<String, String> maFileMap = BaseUtils.getFileMap(Constants.INDICATOR_MA_PATH + "daily/");
        Map<String, String> openTradeFileMap = BaseUtils.getFileMap(Constants.TRADE_PATH + "openFirstTrade/");

        Integer curYear = 2023;
        for (; curYear > 1980; curYear--) {
            Map<String, List<Total>> dateToTotalMap = Maps.newHashMap();
            for (String code : dailyFileMap.keySet()) {
                if (StringUtils.isNotBlank(stockCode) && !StringUtils.equalsIgnoreCase(code, stockCode)) {
                    continue;
                }

                String filePath = dailyFileMap.get(code);
                List<StockKLine> kLines = BaseUtils.loadDataToKline(filePath, curYear, curYear - 1);
                List<MA> maList = loadMA(BaseUtils.readFile(maFileMap.get(code)));
                List<BOLL> bolls = BaseUtils.readBollFile(Constants.HIS_BASE_PATH + "mergeBoll/" + code, curYear, curYear - 1);
                List<BOLL> openBolls = BaseUtils.readBollFile(Constants.HIS_BASE_PATH + "bollWithOpen/" + code, curYear, curYear - 1);
                List<SimpleTrade> openTrades = loadOpenTrade(BaseUtils.readFile(openTradeFileMap.get(code)));
                List<RealOpenVol> f1minTrades = loadF1minTrade(BaseUtils.readFile(Constants.TRADE_OPEN_PATH + curYear + "/" + code));

                List<Total> totals = buildTotalList(code, kLines, maList, bolls, openBolls, openTrades, f1minTrades);
                for (Total total : totals) {
                    String dbYear = total.getDbYear();
                    if (!dateToTotalMap.containsKey(dbYear)) {
                        dateToTotalMap.put(dbYear, Lists.newArrayList());
                    }
                    dateToTotalMap.get(dbYear).add(total);
                }
            }

            importHistoricalDate(dateToTotalMap);
        }
    }

    public void additionToDB() throws Exception {
        log.info("additionToDB begin");
        additionToDB(Lists.newArrayList(), Lists.newArrayList());
        log.info("additionToDB end");
    }

    /**
     * 自定义股票和日期增量导入
     */
    public void additionToDB(List<String> codeList, List<String> dateList) throws Exception {
        int curYear = LocalDate.now().getYear(), lastYear = curYear - 1;

        Map<String, String> dailyFileMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "merge/");
        Map<String, String> maFileMap = BaseUtils.getFileMap(Constants.INDICATOR_MA_PATH + "daily/");
        Map<String, String> openTradeFileMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + curYear + "/openFirstTrade/");

        if (CollectionUtils.isEmpty(dateList)) {
            LocalDate today = LocalDate.now();
            LocalDate yesterday;
            if (today.getDayOfWeek().getValue() == 1) {
                yesterday = today.minusDays(3);
            } else {
                yesterday = today.minusDays(1);
            }

            String yestedayDate = yesterday.format(Constants.DB_DATE_FORMATTER);
            dateList = Lists.newArrayList(yestedayDate);
            curYear = yesterday.getYear();
            lastYear = curYear - 1;
        }

        List<Total> allTotals = Lists.newArrayList();
        for (String code : dailyFileMap.keySet()) {
            if (CollectionUtils.isNotEmpty(codeList) && !codeList.contains(code)) {
                continue;
            }

            String filePath = dailyFileMap.get(code);
            List<StockKLine> kLines = BaseUtils.loadDataToKline(filePath, curYear, lastYear);
            List<MA> maList = loadMA(BaseUtils.readFile(maFileMap.get(code)));
            List<BOLL> bolls = BaseUtils.readBollFile(Constants.HIS_BASE_PATH + "mergeBoll/" + code, curYear, lastYear);
            List<BOLL> openBolls = BaseUtils.readBollFile(Constants.HIS_BASE_PATH + "bollWithOpen/" + code, curYear, lastYear);
            List<SimpleTrade> openTrades = loadOpenTrade(BaseUtils.readFile(openTradeFileMap.get(code)));
            List<RealOpenVol> f1minTrades = loadF1minTrade(BaseUtils.readFile(Constants.TRADE_OPEN_PATH + curYear + "/" + code));

            List<Total> totals = buildTotalList(dateList, code, kLines, maList, bolls, openBolls, openTrades, f1minTrades);
            allTotals.addAll(totals);
        }

        if (CollectionUtils.isEmpty(allTotals)) {
            log.info("there is no date at " + dateList);
            return;
        }

        log.info("sync count: {}", allTotals.size());
        batchInsertFileData(allTotals, curYear);
    }

    public void earningToDB(List<String> dateList) {
        if (CollectionUtils.isEmpty(dateList)) {
            dateList = Lists.newArrayList(LocalDate.now().format(Constants.DB_DATE_FORMATTER));
        }

        List<EarningDate> earningDateList = dateList.stream().map(date -> {
            try {
                return BaseUtils.getAllEarningDate2(date);
            } catch (Exception e) {
                e.printStackTrace();
                return null;
            }
        }).filter(MapUtils::isNotEmpty).flatMap(e -> e.values().stream().flatMap(s -> s.stream())).distinct().collect(Collectors.toList());

        if (CollectionUtils.isEmpty(earningDateList)) {
            log.info(dateList + " has no earning data");
            return;
        }

        earningDataMapper.deleteEarning(dateList);
        earningDataMapper.batchInsertEarning(earningDateList);
        log.info("sync earning finish. dateList=" + dateList + ", earning sum=" + earningDateList.size());
    }

    public void rehabToDB() {
        try {
            List<String> lineList = BaseUtils.readFile(SPLIT_PATH + "rehab");
            List<List<String>> partition = Lists.partition(lineList, 1000);
            for (List<String> lines : partition) {
                List<StockRehab> list = lines.stream().map(StockRehab::convert).collect(Collectors.toList());
                rehabDataMapper.batchInsertRehab(list);
            }
            log.info("finish rehabToDB. size=" + lineList.size());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void batchInsertFileData(List<Total> totalList, int dbYear) {
        String year = String.valueOf(dbYear);
        String exist = stockDataMapper.showTables(year);
        if (StringUtils.isBlank(exist)) {
            stockDataMapper.createTable(year);
        }

        stockDataMapper.batchInsertFileData(totalList, year);
    }

    private void importHistoricalDate(Map<String, List<Total>> dateToTotalMap) {
        for (String year : dateToTotalMap.keySet()) {
            if (!year.equals("2021")) {
                //                continue;
            }
            // 没有表则新建表
            String exist = stockDataMapper.showTables(year);
            if (StringUtils.isBlank(exist)) {
                stockDataMapper.createTable(year);
            }

            List<Total> totalList = dateToTotalMap.get(year);
            List<List<Total>> partition = Lists.partition(totalList, 10000);
            for (List<Total> totals : partition) {
                stockDataMapper.batchInsertFileData(totals, year);
                log.info("write data");
            }

            log.info("finish import {}, count={}", year, totalList.size());
        }
    }

    private List<MA> loadMA(List<String> lineList) {
        if (CollectionUtils.isEmpty(lineList)) {
            return Lists.newArrayListWithExpectedSize(0);
        }
        return lineList.stream().map(MA::convert).collect(Collectors.toList());
    }

    private List<RealOpenVol> loadF1minTrade(List<String> lineList) {
        if (CollectionUtils.isEmpty(lineList)) {
            return Lists.newArrayListWithExpectedSize(0);
        }

        List<RealOpenVol> res = Lists.newArrayList();
        for (String line : lineList) {
            String[] split = line.split(",");
            String date = split[0];
            String volumn = split[1];
            String avgPrice = split[2];
            if (volumn.equals("0") || StringUtils.isBlank(volumn)) {
                continue;
            }

            RealOpenVol realOpenVol = new RealOpenVol();
            realOpenVol.setDate(date);
            realOpenVol.setVolume(Double.valueOf(volumn));
            realOpenVol.setAvgPrice(Double.valueOf(avgPrice));
            res.add(realOpenVol);
        }
        return res;
    }

    private List<SimpleTrade> loadOpenTrade(List<String> lineList) {
        if (CollectionUtils.isEmpty(lineList)) {
            return Lists.newArrayListWithExpectedSize(0);
        }

        List<SimpleTrade> res = Lists.newArrayList();
        for (String line : lineList) {
            String[] split = line.split(",");
            if (split.length < 3) {
                continue;
            }
            String date = split[0];
            double price = Double.parseDouble(split[1]);
            String tradeTime = split[2];

            SimpleTrade openTrade = new SimpleTrade();
            openTrade.setDate(date);
            openTrade.setTradePrice(price);
            openTrade.setTradeTime(tradeTime);

            res.add(openTrade);
        }
        return res;
    }

    private List<Total> buildTotalList(String code, List<StockKLine> kLines, List<MA> maList, List<BOLL> bolls, List<BOLL> openBolls, List<SimpleTrade> openTrades, List<RealOpenVol> f1minTrades) {
        return buildTotalList(null, code, kLines, maList, bolls, openBolls, openTrades, f1minTrades);
    }

    private List<Total> buildTotalList(List<String> dateList, String code, List<StockKLine> kLines, List<MA> maList, List<BOLL> bolls, List<BOLL> openBolls, List<SimpleTrade> openTrades, List<RealOpenVol> f1minTrades) {
        if (CollectionUtils.isEmpty(kLines)) {
            return Lists.newArrayListWithExpectedSize(0);
        }

        Map<String, MA> maMap = maList.stream().collect(Collectors.toMap(MA::getDate, Function.identity(), (v1, v2) -> v1));
        Map<String, BOLL> bollMap = bolls.stream().collect(Collectors.toMap(BOLL::getDate, Function.identity(), (v1, v2) -> v1));
        Map<String, BOLL> openBollMap = openBolls.stream().collect(Collectors.toMap(BOLL::getDate, Function.identity(), (v1, v2) -> v1));
        Map<String, SimpleTrade> openTradeMap = openTrades.stream().collect(Collectors.toMap(SimpleTrade::getDate, Function.identity(), (v1, v2) -> v1));
        Map<String, RealOpenVol> f1minTradeMap = f1minTrades.stream().collect(Collectors.toMap(RealOpenVol::getDate, Function.identity(), (v1, v2) -> v1));

        List<Total> totals = Lists.newArrayList();
        for (StockKLine kLine : kLines) {
            Total total = new Total();
            String date = kLine.getDate();
            String formatDate = BaseUtils.formatDate(date);
            if (CollectionUtils.isNotEmpty(dateList) && !dateList.contains(formatDate)) {
                continue;
            }
            total.setCode(code);
            total.setDbYear(kLine.getDbYear());
            total.setDate(formatDate);
            total.setOpen(kLine.getOpen());
            total.setClose(kLine.getClose());
            total.setHigh(kLine.getHigh());
            total.setLow(kLine.getLow());
            total.setVolume(kLine.getVolume());

            SimpleTrade openTrade = openTradeMap.get(date);
            if (openTrade != null) {
                total.setOpenTrade(openTrade.getTradePrice());
                total.setOpenTradeTime(openTrade.getTradeTime());
            }

            RealOpenVol f1minTrade = f1minTradeMap.get(date);
            if (f1minTrade != null) {
                total.setF1minAvgPrice(f1minTrade.getAvgPrice());
                total.setF1minVolume(f1minTrade.getVolume());
            }

            BOLL boll = bollMap.get(date);
            if (boll != null) {
                total.setMd(boll.getMd());
                total.setMb(boll.getMb());
                total.setUp(boll.getUp());
                total.setDn(boll.getDn());
            }

            BOLL openBoll = openBollMap.get(date);
            if (openBoll != null) {
                total.setOpenMd(openBoll.getMd());
                total.setOpenMb(openBoll.getMb());
                total.setOpenUp(openBoll.getUp());
                total.setOpenDn(openBoll.getDn());
            }

            MA ma = maMap.get(date);
            if (ma != null) {
                total.setMa5(ma.getMa5());
                total.setMa10(ma.getMa10());
                total.setMa20(ma.getMa20());
                total.setMa30(ma.getMa30());
                total.setMa60(ma.getMa60());
            }

            totals.add(total);
        }

        return totals;
    }
}
