package luonq.strategy.backup;

import bean.BOLL;
import bean.Bean;
import bean.StockKLine;
import bean.StockRatio;
import bean.Total;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import luonq.data.ReadFromDB;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import util.BaseUtils;
import util.Constants;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

import static java.math.BigDecimal.ROUND_DOWN;

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
@Component
@Slf4j
public class Strategy_DB {

    public static final String TEST_STOCK = "";
    public static final Set<String> SKIP_SET = Sets.newHashSet("FRC", "SIVBQ");

    Set<String> allCode = Sets.newHashSet();

    /**
     * 2021-2022年开盘布林线
     */
    Map<String/* code */, Map<String/* date */, BOLL>> hisCodeOpenBollMap = Maps.newHashMap();
    /**
     * 2021-2022年k线
     */
    Map<String/* code */, List<StockKLine>> hisKLineMap = Maps.newHashMap();

    @Autowired
    private ReadFromDB readFromDB;

    public void init() throws Exception {
        buildCodeSet();

        LocalDate now = LocalDate.now();
        int lastYear = now.getYear()-1;
        int twoLastYear = lastYear-1;

        ThreadPoolExecutor executor = new ThreadPoolExecutor(3, 3, 5, TimeUnit.MINUTES, new LinkedBlockingQueue<>());
        CountDownLatch cdl = new CountDownLatch(2);
        AtomicReference<List<Total>> _lastYear_Data = new AtomicReference<>();
        executor.submit(() -> {
            try {
                _lastYear_Data.set(readFromDB.getAllYearDate(String.valueOf(lastYear)));
                log.info("load {} finish", lastYear);
            } finally {
                cdl.countDown();
            }
        });
        AtomicReference<List<Total>> _twoLastYear_Data = new AtomicReference<>();
        executor.submit(() -> {
            try {
                _twoLastYear_Data.set(readFromDB.getAllYearDate(String.valueOf(twoLastYear)));
                log.info("load {} finish", twoLastYear);
            } finally {
                cdl.countDown();
            }
        });
        cdl.await();

        List<Total> computeHisDate = Lists.newArrayList();
        computeHisDate.addAll(_lastYear_Data.get());
        computeHisDate.addAll(_twoLastYear_Data.get());
        Map<String, List<Total>> hisCodeTotalMap = computeHisDate.stream().collect(Collectors.groupingBy(Total::getCode, Collectors.toList()));
        hisCodeTotalMap.forEach((code, totals) -> {
            hisKLineMap.put(code, totals.stream().map(Total::toKLine).collect(Collectors.toList()));
            hisCodeOpenBollMap.put(code, totals.stream().collect(Collectors.toMap(Total::getDate, Total::toOpenBoll)));
        });
    }

    private void buildCodeSet() throws Exception {
        LocalDate date = LocalDate.now().minusDays(1);
        int year = date.getYear();
        String day = date.format(Constants.DB_DATE_FORMATTER);
        allCode = Sets.newHashSet(readFromDB.getAllStock(year, day));
        Set<String> invalidStockSet = Sets.newHashSet("FRC", "SIVB", "BIOR", "HALL", "OBLG", "ALBT", "IPDN", "OPGN", "TENX", "AYTU", "DAVE", "NXTP", "ATHE", "CANF", "GHSI", "EEMX", "EFAX", "HYMB", "NYC", "SPYX", "PBLA", "JEF", "ACGN", "EAR", "FWBI", "IDRA", "JFU", "CNET", "APM", "JAGX", "OCSL", "OGEN", "SIEN", "SRKZG", "CETX", "UVIX", "EDBL", "PHIO", "SWVL", "MRKR", "REED", "WISA", "FTFT", "FVCB", "LMNL", "REVB", "DYNT", "BRSF", "LCI", "DGLY", "PCAR", "CZOO", "MIGI", "NAOV", "COMS", "GFAI", "INBS", "SNGX", "APRE", "FNGG", "GNUS", "VYNE", "CRBP", "ATNX", "CFRX", "ECOR", "NVDEF", "SHIP", "AMST", "GMBL", "RELI", "WINT", "FNRN", "MFH", "XBRAF", "RKDA", "HCDI", "IONM", "VXX", "SFT", "VEON", "AKAN", "NYMT", "ORTX", "ASLN", "KRBP", "IVOG", "IVOO", "IVOV", "VIOG", "VIOO", "VIOV", "GRAY", "MRBK", "BAOS", "GGB", "LKCO", "TESTING", "VIA", "IDAI", "PTIX", "RDHL", "CUEN", "FRGT", "GCBC", "ALLR", "CREX", "MTP", "MNST", "NOGN", "BPTS", "CETXP", "ENSC", "HLBZ", "CHNR", "BEST", "MBIO", "WTER", "AGRX", "BLBX", "VBIV", "WISH", "EJH", "ARVL", "MEIP", "MINM", "ASNS", "VERB", "BKTI", "FRSX", "OIG", "LGMK", "POAI", "SMFL", "CLXT", "JXJT", "SBET", "EZFL", "IMPP", "MEME", "PSTV", "VISL", "WEED", "MDRR", "MULN", "WGS", "GTE", "SMH", "CRESY", "BBIG", "HEPA", "AWH", "FRLN", "LPCN", "RETO", "VERO", "ALPP", "BNMV", "EAST", "GLMD", "IFBD", "RETO", "XBIO", "XELA", "XELAP", "CYCN", "GREE", "SDIG", "BIOC", "AULT", "NISN", "CHDN", "LGMK", "HLBZ", "LPCN", "BBIG", "XBIO", "JATT", "TGAA", "GRAY", "GREE", "SDIG", "SMFL", "SMFG", "VERO", "LCI", "TYDE", "DRMA", "BLIN", "HEPA", "SESN", "CR", "LITM", "SNGX", "GE", "MULN", "CGNX", "ML", "MDRR", "PR", "VAL", "EBF", "MTP", "CYCN", "XELA", "ENVX", "EQT", "GLMD", "DCFC", "POAI", "BNOX", "FRLN", "CINC", "NISN", "REFR", "CAPR", "SYRS", "ALPP", "RETO", "VISL", "GNLN", "JXJT", "SAFE", "EZFL", "IDRA", "CRESY", "IMPP", "ZEV", "EAST", "BIOC", "IFBD", "STAR", "AWH", "TNXP", "WORX", "VLON", "PSTV", "SFT", "AGRX", "MBIO", "APRE", "GAME", "VERB", "CFRX", "BLBX", "COMS", "RKDA", "WISH", "NXTP", "TR", "ARVL", "EJH", "MEIP", "ENSC", "NYMT", "PNTM", "ASNS", "AKAN", "RDFN", "GMBL", "VYNE", "MNST", "LCAA", "FRSX", "CRBP", "ATNX", "OIG", "REED", "OUST", "ALLR", "NAOV", "KRBP", "ICMB", "XOS", "GFAI", "GNUS", "BGXX", "FTFT", "AMST", "FCUV", "VBIV", "BIIB", "MINM", "CLXT", "DGLY", "MRKR");
        allCode.removeAll(invalidStockSet);
        BaseUtils.filterStock(allCode);
    }

    public Map<String, StockRatio> computeHisOverBollingerRatio() {
        Map<String, StockRatio> stockRatioMap = Maps.newHashMap();
        for (String stock : allCode) {
            if (StringUtils.isNotBlank(TEST_STOCK) && !stock.equals(TEST_STOCK)) {
                continue;
            }
            if (SKIP_SET.contains(stock)) {
                continue;
            }

            List<StockKLine> kLines = hisKLineMap.get(stock);
            Map<String, BOLL> dateToOpenBollMap = hisCodeOpenBollMap.get(stock);
            List<Bean> result = strategy(kLines, dateToOpenBollMap);

            StockRatio stockRatio = new StockRatio();
            result.stream().forEach(r -> stockRatio.addBean(r));
            stockRatioMap.put(stock, stockRatio);
        }

        return stockRatioMap;
    }

    private static List<Bean> strategy(List<StockKLine> stockKLines, Map<String, BOLL> bollWithOpen) {
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

        bean.setCloseLessOpen(close > open ? 1 : 0);
        return bean;
    }
}
