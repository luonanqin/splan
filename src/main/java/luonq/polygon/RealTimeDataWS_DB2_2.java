package luonq.polygon;

import bean.BOLL;
import bean.FrontReinstatement;
import bean.NodeList;
import bean.RatioBean;
import bean.SplitStockInfo;
import bean.StockEvent;
import bean.StockKLine;
import bean.StockPosition;
import bean.StockRatio;
import bean.StockRehab;
import bean.StopLoss;
import bean.Total;
import bean.TradeCalendar;
import com.alibaba.fastjson.JSON;
import com.futu.openapi.FTAPI;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.eventbus.AsyncEventBus;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import luonq.data.ReadFromDB;
import luonq.execute.LoadOptionTradeData;
import luonq.execute.ReadWriteOptionTradeInfo;
import luonq.futu.BasicQuote;
import luonq.ibkr.TradeApi;
import luonq.listener.OptionStockListener2_2;
import luonq.strategy.backup.Strategy_DB;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import util.BaseUtils;
import util.Constants;

import javax.websocket.ClientEndpoint;
import javax.websocket.CloseReason;
import javax.websocket.ContainerProvider;
import javax.websocket.OnClose;
import javax.websocket.OnMessage;
import javax.websocket.OnOpen;
import javax.websocket.Session;
import javax.websocket.WebSocketContainer;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URI;
import java.nio.ByteBuffer;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@ClientEndpoint
@Slf4j
public class RealTimeDataWS_DB2_2 {

    public static final String TEST_STOCK = "";
    public static final Set<String> invalidStockSet = Sets.newHashSet("SLAC", "CVCY", "SFE", "STRC", "APAC", "BHG", "NVIV", "SLGC", "SCTL", "LGST", "LGVC", "JAQC", "SRC", "NEPT", "NVTA", "CNDB", "NETI", "BVH", "BKCC", "GRCL", "BYN", "APTM", "KLR", "QFTA", "PCTI", "RAIN", "TCN", "GRPH", "MCAF", "TGH", "CNXA", "CHS", "KERN", "CPE", "LCA", "DISA", "DISH", "SECO", "SEAS", "BLCM", "SVFD", "LIAN", "CYT", "KNTE", "THRX", "SNCE", "PUYI", "GBNH", "WRAC", "MDGS", "PEAK", "NGMS", "ARIZ", "JT", "LIZI", "MDC", "DSKE", "ARRW", "RCAC", "PEPL", "MDVL", "ENCP", "EAC", "ENER", "EAR", "RCII", "LAZY", "CPSI", "CHEA", "ASAP", "ASCA", "LBBB", "FICV", "TRKA", "TRMR", "SOLO", "DCFC", "NGM", "SGEN", "SOVO", "KYCH", "FIXX", "FRLN", "ATAK", "GMDA", "ACAX", "VYNT", "LCAA", "ATCX", "RMGC", "ATHX", "LTHM", "SPLK", "FAZE", "EGGF", "FSR", "FUV", "CRGE", "ACOR", "EGLE", "BODY", "EXPR", "ACRX", "HHLA", "DMAQ", "OPA", "PGTI", "VIEW", "PGSS", "OSA", "CASA", "GIA", "ALTU", "ADEX", "ADES", "JOAN", "GOL", "ALYA", "CBAY", "GTH", "BXRX", "ADOC", "AMAM", "KAMN", "SIEN", "XPDB", "ICVX", "AMEH", "VAQC", "LMDX", "KRTX", "PNT", "VJET", "RWLK", "ONCR", "NSTG", "CSTR", "JGGC", "AMNB", "PXD", "AMTI", "DWAC", "SZZL", "ONTX", "NTCO", "LEJU", "EIGR", "NCAC", "LVOX", "HARP", "FLME", "SASI", "IMGN", "ROVR", "WETG", "CCLP", "TMST", "TEDU", "WNNR", "CLIN", "RAD", "CDAY", "NUBI", "AEL", "ESAC", "PBAX", "MIXT", "RPT", "CURO", "OXUS", "STAR", "EBIX", "MRTX", "AYX", "OHAA", "TWOA", "PBTS", "IOAC", "HCMA", "DHCA", "FRC", "SIVB", "BIOR", "HALL", "OBLG", "ALBT", "IPDN", "OPGN", "TENX", "AYTU", "DAVE", "NXTP", "ATHE", "CANF", "GHSI", "EEMX", "EFAX", "HYMB", "NYC", "SPYX", "PBLA", "JEF", "ACGN", "FWBI", "IDRA", "JFU", "CNET", "APM", "JAGX", "OCSL", "OGEN", "SRKZG", "CETX", "UVIX", "EDBL", "PHIO", "SWVL", "MRKR", "REED", "WISA", "FTFT", "FVCB", "LMNL", "REVB", "DYNT", "BRSF", "LCI", "DGLY", "PCAR", "CZOO", "MIGI", "NAOV", "COMS", "GFAI", "INBS", "SNGX", "APRE", "FNGG", "GNUS", "VYNE", "CRBP", "ATNX", "CFRX", "ECOR", "NVDEF", "SHIP", "AMST", "GMBL", "RELI", "WINT", "FNRN", "MFH", "XBRAF", "RKDA", "HCDI", "IONM", "VXX", "SFT", "VEON", "AKAN", "NYMT", "ORTX", "ASLN", "KRBP", "IVOG", "IVOO", "IVOV", "VIOG", "VIOO", "VIOV", "GRAY", "MRBK", "BAOS", "GGB", "LKCO", "TESTING", "VIA", "IDAI", "PTIX", "RDHL", "CUEN", "FRGT", "GCBC", "ALLR", "CREX", "MTP", "MNST", "NOGN", "BPTS", "CETXP", "ENSC", "HLBZ", "CHNR", "BEST", "MBIO", "WTER", "AGRX", "BLBX", "VBIV", "WISH", "EJH", "ARVL", "MEIP", "MINM", "ASNS", "VERB", "BKTI", "FRSX", "OIG", "LGMK", "POAI", "SMFL", "CLXT", "JXJT", "SBET", "EZFL", "IMPP", "MEME", "PSTV", "VISL", "WEED", "MDRR", "MULN", "WGS", "GTE", "SMH", "CRESY", "BBIG", "HEPA", "AWH", "LPCN", "RETO", "VERO", "ALPP", "BNMV", "EAST", "GLMD", "IFBD", "XBIO", "XELA", "XELAP", "CYCN", "GREE", "SDIG", "BIOC", "AULT", "NISN", "CHDN", "JATT", "TGAA", "SMFG", "TYDE", "DRMA", "BLIN", "SESN", "CR", "LITM", "GE", "CGNX", "ML", "PR", "VAL", "EBF", "ENVX", "EQT", "BNOX", "CINC", "REFR", "CAPR", "SYRS", "GNLN", "SAFE", "ZEV", "TNXP", "WORX", "VLON", "GAME", "TR", "PNTM", "RDFN", "OUST", "ICMB", "XOS", "BGXX", "FCUV", "BIIB");
    public static final URI uri = URI.create("wss://socket.polygon.io/stocks");
    public static final double HIT = 0.5d; // 策略成功率
    public static final int PRICE_LIMIT = 7; // 价格限制，用于限制这个价格下的股票不参与计算
    public static final double LOSS_RATIO = 0.07d; // 止损比例
    public static final int DELAY_MINUTE = 0;
    public static final long LISTENING_TIME = 20000L; // 监听时长，毫秒
    private static LocalDateTime summerTime = BaseUtils.getSummerTime(null);
    private static LocalDateTime winterTime = BaseUtils.getWinterTime(null);

    private boolean subscribed = false;
    private boolean listenStopLoss = false;
    private boolean reconnect = false;
    private boolean manualClose = false;
    public static Map<String, Double> stockToLastDn = Maps.newHashMap();
    public static Map<String, Double> stockToM19CloseSum = Maps.newHashMap();
    public static Map<String, List<Double>> stockToM19Close = Maps.newHashMap();
    public static Map<String, StockRatio> originRatioMap = Maps.newHashMap();
    public static Set<String> todayEarningStockSet = Sets.newHashSet();
    public static Set<String> lastEarningStockSet = Sets.newHashSet();
    public static boolean getRealtimeQuote = false;
    public static boolean getRealtimeQuoteForOption = false;
    public static boolean getQuoteForOption = false;
    public static Map<String, Double> realtimeQuoteMap = Maps.newHashMap();
    public static Map<String, Double> realtimeQuoteForOptionMap = Maps.newHashMap();
    private BlockingQueue<String> subscribeBQ = new LinkedBlockingQueue<>(1000);
    private BlockingQueue<String> stopLossBQ = new LinkedBlockingQueue<>(1000);
    private BlockingQueue<String> realtimeQuoteBQ = new LinkedBlockingQueue<>(1000);
    private BlockingQueue<String> optionQuoteBQ = new LinkedBlockingQueue<>(1000);
    private ThreadPoolExecutor executor;
    private long preTradeTime;
    private long openTime;
    private long listenEndTime;
    @Getter
    @Setter
    private int season;
    private Date closeCheckTime;
    private boolean listenEnd = false;
    private NodeList list = new NodeList(10);
    private AtomicBoolean hasAuth = new AtomicBoolean(false);
    private double funds;

    public static Set<String> stockSet = Sets.newHashSet();
    public Set<String> allStockSet = Sets.newHashSet();
    public Set<String> filterStockSet = Sets.newHashSet();
    private static Set<String> unsubcribeStockSet = Sets.newHashSet();
    private AsyncEventBus tradeEventBus;
    private Session userSession = null;
    private boolean testOption = true;
    private boolean unsubscribe = false;

    private List<String> optionStockSet;
    private OptionTradeExecutor2_2 optionTradeExecutor2;
    private OptionStockListener2_2 optionStockListener2;
    //    private OptionTradeExecutor3 optionTradeExecutor3;
    //    private OptionStockListener3 optionStockListener3;
    private TradeApi tradeApi;

    @Autowired
    private TradeExecutor_DB tradeExecutor;

    @Autowired
    private Strategy_DB strategy;

    @Autowired
    private ReadFromDB readFromDB;

    @Autowired
    private LoadOptionTradeData loadOptionTradeData;

    public long getOpenTime() {
        return openTime;
    }

    public void setOpenTime(long openTime) {
        this.openTime = openTime;
    }

    public double getFunds() {
        return funds;
    }

    public void init() {
        try {
            initThreadExecutor();
            initVariable();
            initManyTime();
            connect();
            boolean authSuccess = waitAuth();
            if (!authSuccess) {
                return;
            }

            initTrade();

            if (testOption) {
                initHistoricalData();
                initMessageListener();
                //                if (MapUtils.isNotEmpty(optionTradeExecutor.getAllPosition())) {
                //                    optionTradeExecutor.reSendOpenPrice();
                //                    Thread.sleep(3000);
                //                    getRealtimeQuoteForOption();
                //                    optionTradeExecutor.restart();
                //                } else {
                subcribeStock();
                //                sendToTradeDataListener();

                executor.execute(() -> sendToOptionListener());
                beginTrade();
                //                }
            } else {
                if (MapUtils.isNotEmpty(tradeExecutor.getAllPosition())) {
                    testOption = true;
                }
                initHistoricalData();
                initMessageListener();
                subcribeStock();
                sendToTradeDataListener();
            }
            close();
            log.info("trade finish");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void initVariable() {
        userSession = null;
        subscribed = false;
        listenStopLoss = false;
        reconnect = false;
        manualClose = false;
        stockToLastDn = Maps.newHashMap();
        stockToM19CloseSum = Maps.newHashMap();
        stockToM19Close = Maps.newHashMap();
        todayEarningStockSet = Sets.newHashSet();
        lastEarningStockSet = Sets.newHashSet();
        getRealtimeQuote = false;
        getRealtimeQuoteForOption = false;
        realtimeQuoteMap = Maps.newHashMap();
        subscribeBQ = new LinkedBlockingQueue<>(1000);
        stopLossBQ = new LinkedBlockingQueue<>(1000);
        realtimeQuoteBQ = new LinkedBlockingQueue<>(1000);
        listenEnd = false;
        list = new NodeList(10);
        hasAuth = new AtomicBoolean(false);
        unsubcribeStockSet = Sets.newHashSet();
        filterStockSet = Sets.newHashSet();
        tradeEventBus = asyncEventBus();
        unsubscribe = false;
        optionStockListener2 = new OptionStockListener2_2();
        //        optionStockListener3 = new OptionStockListener3();
    }

    private boolean waitAuth() {
        int times = 0;
        while (true) {
            if (hasAuth.getAndSet(false)) {
                return true;
            }
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
            }
            times++;
            if (times > 10) {
                log.info("auth failed");
                //                System.exit(0);
                return false;
            }
        }
    }

    public void initHistoricalData() {
        try {
            loadOptionTradeData.load();
            optionTradeExecutor2.init();
            //            optionTradeExecutor3.init();
            //            optionTradeExecutor.getRealTimeIV();
            optionTradeExecutor2.getFutuRealTimeIV();
            optionTradeExecutor2.getPolygonRealTimeGreeks();
            //            optionTradeExecutor3.getFutuRealTimeIV();
            ReadWriteOptionTradeInfo.init();
            if (!testOption) {
                computeHisOverBollingerRatio();
                loadEarningInfo();
                stockSet = buildStockSet();
            }
            //            stockSet.add("AAPL"); // todo 测试用需删除
            optionStockSet = LoadOptionTradeData.stocks;
            // dn策略强制删除option涉及到的股票
            //            stockSet.removeAll(optionStockSet);

            allStockSet.addAll(stockSet);
            allStockSet.addAll(optionStockSet);
            allStockSet.removeAll(LoadOptionTradeData.invalidStocks);
            //            stockSet.clear();
            //            stockSet.add("RNST");

            //            loadLastDn();
            //            loadLatestMA20();

            log.info("finish init historical data");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void initTrade() {
        try {
            tradeApi = new TradeApi();
            TimeUnit.SECONDS.sleep(5);
            //            double pnl = tradeApi.getPnl();
            //            funds = tradeApi.getAccountCash();
            //            funds = funds - pnl;
            //            if (funds < 200) {
            //                funds = 1000;
            //                log.info("funds is less than 200, init {}", funds);
            //            } else {
            //                log.info("funds is {}", funds);
            //            }
            //            funds = tradeApi.getAvailableCash() * 0.92;
            funds = 4177d * 0.92;

            FTAPI.init();
            BasicQuote futuQuote = new BasicQuote();
            futuQuote.start();

            optionTradeExecutor2 = new OptionTradeExecutor2_2();
            optionTradeExecutor2.setFutuQuote(futuQuote);
            optionTradeExecutor2.setTradeApi(tradeApi);
            optionTradeExecutor2.setClient(this);
            optionTradeExecutor2.setOptionStockListener(optionStockListener2);
            optionStockListener2.setOptionTradeExecutor(optionTradeExecutor2);

            //            optionTradeExecutor3 = new OptionTradeExecutor3();
            //            optionTradeExecutor3.setFutuQuote(futuQuote);
            //            optionTradeExecutor3.setTradeApi(tradeApi);
            //            optionTradeExecutor3.setClient(this);
            //            optionTradeExecutor3.setOptionStockListener(optionStockListener3);
            //            optionStockListener3.setOptionTradeExecutor(optionTradeExecutor3);
            log.info("finish init trade");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void initMessageListener() {
        try {
            tradeEventBus.register(optionStockListener2);
            //            tradeEventBus.register(optionStockListener3);

            log.info("finish init message listener");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void connect() {
        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.connectToServer(this, uri);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public Date getCloseCheckTime() {
        return closeCheckTime;
    }

    public void setCloseCheckTime(Date closeCheckTime) {
        this.closeCheckTime = closeCheckTime;
    }

    private void computeHisOverBollingerRatio() throws Exception {
        strategy.init();
        originRatioMap = strategy.computeHisOverBollingerRatio();
    }

    public void initManyTime() {
        LocalDateTime now = LocalDateTime.now();
        boolean beforeDawn = now.getHour() < 10; // 小于10则表示新的一天凌晨
        LocalDateTime preTrade;
        LocalDateTime openTrade;
        LocalDateTime closeCheck;
        if (!beforeDawn) {
            preTrade = now;
            openTrade = now;
            closeCheck = now.plusDays(1);
        } else {
            preTrade = now.minusDays(1);
            openTrade = now.minusDays(1);
            closeCheck = now;
        }

        long checkOpenTime = 0;
        LocalDateTime checkPre, checkOpen;
        int openHour, closeHour, preMin = 28 + DELAY_MINUTE, openMin = 30;
        TradeCalendar tradeCalendar = readFromDB.getTradeCalendar(closeCheck.minusDays(1).format(Constants.DB_DATE_FORMATTER));
        if (openTrade.isAfter(summerTime) && openTrade.isBefore(winterTime)) {
            openHour = 21;
            closeHour = 3;
            if (tradeCalendar != null && tradeCalendar.getType() == 1) {
                closeHour = closeHour - 3;
            }
            checkPre = preTrade.withHour(21).withMinute(22).withSecond(0).withNano(0);
            checkOpen = openTrade.withHour(21).withMinute(30).withSecond(0).withNano(0);
            checkOpenTime = checkOpen.toInstant(ZoneOffset.of("+8")).toEpochMilli();
            season = -4;
        } else {
            openHour = 22;
            closeHour = 4;
            if (tradeCalendar != null && tradeCalendar.getType() == 1) {
                closeHour = closeHour - 3;
            }
            checkPre = preTrade.withHour(22).withMinute(22).withSecond(0).withNano(0);
            checkOpen = openTrade.withHour(22).withMinute(30).withSecond(0).withNano(0);
            checkOpenTime = checkOpen.toInstant(ZoneOffset.of("+8")).toEpochMilli();
            season = -5;
        }
        Instant preTradeTimeInst = preTrade.withHour(openHour).withMinute(preMin).withSecond(0).withNano(0).toInstant(ZoneOffset.of("+8"));
        preTradeTime = preTradeTimeInst.toEpochMilli();
        Instant openTimeInst = openTrade.withHour(openHour).withMinute(openMin).withSecond(0).withNano(0).toInstant(ZoneOffset.of("+8"));
        openTime = openTimeInst.toEpochMilli();
        closeCheckTime = Date.from(closeCheck.withHour(closeHour).withMinute(59).withSecond(0).withNano(0).toInstant(ZoneOffset.of("+8")));
        listenEndTime = openTime + LISTENING_TIME;

        // check the open time
        if (openTrade.isAfter(checkPre) && openTrade.isBefore(checkOpen) && openTime != checkOpenTime) {
            log.error("open time is illegal!!! please change it");
            System.exit(0);
        }

        log.info("finish initialize many time. preTradeTime=" + Date.from(preTradeTimeInst) + ", openTime=" + Date.from(openTimeInst) + ", closeCheckTime=" + closeCheckTime);
    }

    private void initThreadExecutor() {
        int threadCount = 2000;
        int corePoolSize = threadCount;
        int maximumPoolSize = corePoolSize;
        long keepAliveTime = 60L;
        LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();
        executor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.MINUTES, workQueue);
    }

    public void loadEarningInfo() {
        LocalDate now = LocalDate.now();
        String today = now.format(Constants.DB_DATE_FORMATTER);
        todayEarningStockSet = Sets.newHashSet(readFromDB.getStockForEarning(today));

        String yesterday;
        if (now.getDayOfWeek().getValue() == 1) {
            yesterday = now.minusDays(3).format(Constants.DB_DATE_FORMATTER);
        } else {
            yesterday = now.minusDays(1).format(Constants.DB_DATE_FORMATTER);
        }
        lastEarningStockSet = Sets.newHashSet(readFromDB.getStockForEarning(yesterday));
    }

    private Set<String> buildStockSet() throws Exception {
        LocalDate now = LocalDate.now();
        LocalDate yesterday = now.minusDays(1);
        LocalDate standard = now.minusDays(4);
        List<Total> latestData;
        while (true) {
            int year = yesterday.getYear();
            String date = yesterday.format(Constants.DB_DATE_FORMATTER);
            latestData = readFromDB.getAllStockData(year, date);
            if (CollectionUtils.isNotEmpty(latestData)) {
                break;
            } else {
                yesterday = yesterday.minusDays(1);
            }
        }

        Map<String, List<StockKLine>> curKLineMap = strategy.getCurKLineMap();

        // 过滤前日收盘价低于CLOSE_PRICE 和 前日成交量小于10w的
        Set<String> set = Sets.newHashSet();
        for (Total total : latestData) {
            String code = total.getCode();
            double close = total.getClose();
            double open = total.getOpen();
            double volume = total.getVolume().doubleValue();
            String date = total.getDate();
            LocalDate parse = LocalDate.parse(date, Constants.DB_DATE_FORMATTER);
            if (parse.isBefore(standard)) {
                continue;
            }
            List<StockKLine> stockKLines = curKLineMap.get(code);
            long volumnLess = stockKLines.stream().limit(19).filter(k -> k.getVolume().doubleValue() < 100000).count();
            if (volumnLess == 0 && close <= open) {
                set.add(code);
            }
        }
        log.info(String.format("stock latest close great %d size: %d. data: %s", PRICE_LIMIT, set.size(), set));

        // 过滤所有OverBolling策略命中率低于HIT
        for (String stock : originRatioMap.keySet()) {
            StockRatio stockRatio = originRatioMap.get(stock);
            Map<Integer, RatioBean> ratioMap = stockRatio.getRatioMap();
            boolean hitFailed = true;
            for (RatioBean ratio : ratioMap.values()) {
                double ratioVal = ratio.getRatio();
                if (ratioVal >= HIT) {
                    hitFailed = false;
                }
            }
            if (hitFailed) {
                set.remove(stock);
            }
        }
        log.info(String.format("stock overbolling hit great %f size: %d. data: %s", HIT, set.size(), set));

        // 过滤所有合股
        Set<String> mergeStock = BaseUtils.getMergeStock();
        set.removeAll(mergeStock);
        log.info(String.format("filter merge stock, the stock set size is %s. data: %s", set.size(), set));

        // 过滤所有拆股
        Set<SplitStockInfo> splitStockInfo = BaseUtils.getSplitStockInfo();
        Set<String> splitStock = splitStockInfo.stream().map(SplitStockInfo::getStock).collect(Collectors.toSet());
        set.removeAll(splitStock);
        log.info(String.format("filter split stock, the stock set size is %d. data: %s", set.size(), set));

        // 过滤所有今年前复权因子低于0.98的
        int year = now.getYear() - 1;
        LocalDate firstDay = LocalDate.parse(year + "-01-01", DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        Set<FrontReinstatement> reinstatementInfo = BaseUtils.getFrontReinstatementInfo();
        Map<String, FrontReinstatement> map = reinstatementInfo.stream().collect(Collectors.toMap(FrontReinstatement::getStock, Function.identity()));
        for (String stock : map.keySet()) {
            StockRehab latestRehab = readFromDB.getLatestRehab(stock);
            String date = latestRehab.getDate();
            double fwdFactorA = latestRehab.getFwdFactorA();
            if (fwdFactorA > 0.98) {
                continue;
            }
            //            FrontReinstatement fr = map.get(stock);
            //            double factor = fr.getFactor();
            //            if (factor > 0.98) {
            //                continue;
            //            }

            //            String date = fr.getDate();
            LocalDate dateParse = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            if (dateParse.isAfter(firstDay)) {
                set.remove(stock);
            }
        }
        log.info(String.format("filter front reinstatement less 0.98 stock, the stock set size is %d. data: %s", set.size(), set));

        // 财报当天和财报后一天都不进行交易
        set.removeAll(todayEarningStockSet);
        set.removeAll(lastEarningStockSet);
        log.info(String.format("filter earning and lastEarning stock, the stock set size is %d. data: %s", set.size(), set));

        // 历史搜集的无效股票
        set.removeAll(invalidStockSet);

        log.info("after filte: " + set);
        return set;
    }

    public void loadLatestMA20() {
        Map<String, List<StockKLine>> curKLineMap = strategy.getCurKLineMap();
        for (String stock : curKLineMap.keySet()) {
            if (StringUtils.isNotBlank(TEST_STOCK) && !stock.equals(TEST_STOCK)) {
                continue;
            }
            List<StockKLine> kLines = curKLineMap.get(stock);
            if (kLines.size() < 19) {
                continue;
            }
            BigDecimal m20close = BigDecimal.ZERO;
            List<Double> _19Close = Lists.newArrayList();
            for (int i = 0; i < 19; i++) {
                double close = kLines.get(i).getClose();
                m20close = m20close.add(BigDecimal.valueOf(close));
                _19Close.add(close);
            }

            stockToM19CloseSum.put(stock, m20close.doubleValue());
            stockToM19Close.put(stock, _19Close);
        }
        log.info("finish load lastest 19-days close data");
    }

    private void loadLastDn() {
        Map<String, List<BOLL>> curKLineMap = strategy.getCurBollMap();
        for (String stock : curKLineMap.keySet()) {
            if (StringUtils.isNotBlank(TEST_STOCK) && !stock.equals(TEST_STOCK)) {
                continue;
            }
            List<BOLL> bolls = curKLineMap.get(stock);
            if (CollectionUtils.isEmpty(bolls)) {
                continue;
            }
            BOLL boll = bolls.get(0);
            double dn = boll.getDn();
            stockToLastDn.put(stock, dn);
        }
        log.info("finish load last DN for BOLL");
    }

    public AsyncEventBus asyncEventBus() {
        return new AsyncEventBus(executor);
    }

    /**
     * Callback hook for Connection open events.
     *
     * @param userSession the userSession which is opened.
     */
    @OnOpen
    public void onOpen(Session userSession) {
        log.info("opening websocket");
        this.userSession = userSession;
    }

    /**
     * Callback hook for Connection close events.
     *
     * @param userSession the userSession which is getting closed.
     * @param reason      the reason for connection close
     */
    @OnClose
    public void onClose(Session userSession, CloseReason reason) {
        log.info("closing stock websocket. reason=" + reason);
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
        }
        if (manualClose) {
            log.info("manual close websocket");
            return;
        }
        reconnect();
        this.userSession = userSession;
    }

    public void close() throws Exception {
        if (userSession != null) {
            manualClose = true;
            userSession.close();
            tradeApi.end();
            executor.shutdown();
        }
    }

    /**
     * Callback hook for Message Events. This method will be invoked when a client send a message.
     *
     * @param message The text message
     */
    @OnMessage
    public void onMessage(String message) {
        try {
            if (subscribed) {
                //                log.info(message);
                subscribeBQ.offer(message);
            } else if (listenStopLoss) {
                stopLossBQ.offer(message);
            } else if (getRealtimeQuote || getRealtimeQuoteForOption) {
                realtimeQuoteBQ.offer(message);
            } else {
                List<Map> maps = JSON.parseArray(message, Map.class);
                Map map = maps.get(0);
                String status = MapUtils.getString(map, "status");
                String msg = MapUtils.getString(map, "message");

                if ("connected".equals(status) && "Connected Successfully".equals(msg)) {
                    log.info(status);
                    sendMessage("{\"action\":\"auth\",\"params\":\"Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY\"}");
                } else if ("auth_success".equals(status) && "authenticated".equals(msg)) {
                    log.info(status);
                    hasAuth.set(true);
                }
            }
        } catch (
          Exception e) {
            log.info("onMessage " + e.getMessage());
        }
    }

    private void subcribeStock() throws InterruptedException {
        subscribed = true;
        while (true) {
            LocalDateTime now = LocalDateTime.now();
            long nowTime = now.toInstant(ZoneOffset.of("+8")).toEpochMilli();
            if (nowTime < preTradeTime) {
                log.info("wait pre trade time. now is " + now.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    throw e;
                }
            } else {
                log.info("begin subcribe stock at " + now.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                break;
            }
        }

        executor.execute(() -> {
            for (String stock : allStockSet) {
                sendMessage("{\"action\":\"subscribe\", \"params\":\"T." + stock + "\"}");
            }
            log.info("finish subcribe real time!");
        });
    }

    @OnMessage
    public void onMessage(ByteBuffer bytes) {
        log.info("Handle byte buffer");
    }

    /**
     * Send a message.
     *
     * @param message
     */
    public void sendMessage(String message) {
        this.userSession.getAsyncRemote().sendText(message);
    }

    public void sendToOptionListener() {
        try {
            while (true) {
                String msg = subscribeBQ.poll(2, TimeUnit.SECONDS);
                if (listenEnd) {
                    return;
                }
                long currentTime = System.currentTimeMillis();
                if (StringUtils.isBlank(msg)) {
                    log.info("there is no msg. continue");
                    continue;
                }

                //                log.info(msg);
                List<Map> maps = JSON.parseArray(msg, Map.class);
                for (Map map : maps) {
                    String stock = MapUtils.getString(map, "sym", "");

                    if (StringUtils.isBlank(stock)) {
                        continue;
                    }
                    Double price = MapUtils.getDouble(map, "p");
                    Long time = MapUtils.getLong(map, "t");
                    Integer size = MapUtils.getInteger(map, "s");
                    if (size < 100) {
                        continue;
                    }
                    if (time < openTime) {
                        if (time % 100 == 0) {
                            log.info("time is early. " + map);
                        }
                        continue;
                    }

                    realtimeQuoteForOptionMap.put(stock, price);
                    if (filterStockSet.contains(stock)) {
                        continue;
                    } else {
                        // 发出事件待交易
                        tradeEventBus.post(new StockEvent(stock, price, time));
                        filterStockSet.add(stock);
                    }
                }

                // 监听时间到达后1秒，反订阅不能交易的股票。反订阅只执行一次，所以要有标记位
                if (!unsubscribe && currentTime > listenEndTime + 1000) {
                    log.info("now time is over! listen end!");
                    Set<String> canTradeStocks = optionStockListener2.getCanTradeStocks();
                    for (String s : allStockSet) {
                        if (!canTradeStocks.contains(s)) {
                            unsubscribe(s);
                        }
                    }
                    unsubscribe = true;
                }
            }
        } catch (Exception e) {
            log.info("sendToOptionListener error. ", e);
        }
    }

    public void beginTrade() throws Exception {
        log.info("begin wait trade");
        while (System.currentTimeMillis() < listenEndTime) {
            TimeUnit.SECONDS.sleep(1);
            log.info("waiting trade......");
        }

        ReadWriteOptionTradeInfo.writeStockOpenPrice();
        optionTradeExecutor2.beginTrade();
    }

    public void stopListen() {
        listenEnd = true;
    }

    public void sendToTradeDataListener() {
        try {
            while (true) {
                String msg = subscribeBQ.poll(2, TimeUnit.SECONDS);
                if (listenEnd) {
                    return;
                }
                long currentTime = System.currentTimeMillis();
                if (StringUtils.isBlank(msg)) {
                    if (currentTime > listenEndTime) {
                        beginTrade("time over. listen end!");
                        return;
                    } else if (allStockSet.size() - unsubcribeStockSet.size() < 100) {
                        if (listenEndTime - currentTime > 10000) {
                            log.info("many stock has received data. keep listening");
                            continue;
                        }
                        beginTrade("many stock has received data. listen end!");
                        return;
                    } else {
                        log.info("there is no msg. continue");
                        continue;
                    }
                }

                //                log.info(msg);
                List<Map> maps = JSON.parseArray(msg, Map.class);
                Map<String, StockEvent> stockToEvent = Maps.newHashMap();
                for (Map map : maps) {
                    String stock = MapUtils.getString(map, "sym", "");

                    if (unsubcribeStockSet.contains(stock) || StringUtils.isBlank(stock)) {
                        //                        log.info(msg);
                        continue;
                    }
                    Double price = MapUtils.getDouble(map, "p");
                    Long time = MapUtils.getLong(map, "t");
                    Integer size = MapUtils.getInteger(map, "s");
                    if (size < 100) {
                        continue;
                    }
                    if (time < openTime) {
                        if (time % 1000 == 0) {
                            log.info("time is early. " + map);
                        }
                        continue;
                    }
                    if (time > listenEndTime) {
                        beginTrade(stock + " time is " + time + ", price is " + price + ".listen end!");
                        return;
                    }

                    //                    log.info("receive data: {}", msg);
                    stockToEvent.put(stock, new StockEvent(stock, price, time));
                }

                // 用当前时间判断是否超过监听时间，避免不活跃股票 或 接口延迟 导致监听超时，影响及时下单
                if (currentTime > listenEndTime) {
                    beginTrade("now time is over! listen end!");
                    return;
                }

                // 发出事件待交易
                for (String stock : stockToEvent.keySet()) {
                    if (unsubscribe(stock)) {
                        tradeEventBus.post(stockToEvent.get(stock));
                    }
                }

                // 所有股票都收到了开盘价，停止监听开始交易
                if (allStockSet.size() == unsubcribeStockSet.size()) {
                    beginTrade("receive all open price! start trade!");
                    return;
                }
            }
        } catch (Exception e) {
            log.info("sendToTradeDataListener error. ", e);
        }
    }

    public void beginTrade(String msg) throws Exception {
        Thread.sleep(1000);
        subscribed = false;
        log.info(msg);
        unsubscribeAll();
        listenEnd = true;
        getRealtimeQuoteForOption(optionStockListener2.getCanTradeStocks());
        ReadWriteOptionTradeInfo.writeStockOpenPrice();
        //        asynCheckExecutor3();
        optionTradeExecutor2.beginTrade();
        //        optionTradeExecutor3.beginTrade();
    }

    //    private void asynCheckExecutor3() {
    //        executor.execute(() -> {
    //            while (true) {
    //                if (optionTradeExecutor3.checkNoPosition()) {
    //                    try {
    //                        TimeUnit.SECONDS.sleep(1);
    //                    } catch (InterruptedException e) {
    //                    }
    //                    if (System.currentTimeMillis() > optionTradeExecutor3.getOpenTime()) {
    //                        log.info("check executor3 is over time");
    //                        return;
    //                    }
    //                } else {
    //                    optionTradeExecutor3.cannotTrade();
    //                    optionTradeExecutor3.cancelMonitor();
    //                    log.info("check executor3 has position");
    //                    return;
    //                }
    //            }
    //        });
    //    }

    public void getRealtimeQuoteForOption(Set<String> stockSet) throws InterruptedException {
        getRealtimeQuoteForOption = true;
        if (CollectionUtils.isEmpty(stockSet)) {
            log.info("there is no stock to get real-time quote for option");
            return;
        }
        log.info("get real-time quote for option: {}", stockSet);
        for (String stock : stockSet) {
            sendMessage("{\"action\":\"subscribe\", \"params\":\"Q." + stock + "\"}");
        }

        executor.execute(() -> {
            try {
                while (getRealtimeQuoteForOption) {
                    String msg = realtimeQuoteBQ.poll(1, TimeUnit.SECONDS);
                    if (StringUtils.isBlank(msg)) {
                        continue;
                    }

                    List<Map> maps = JSON.parseArray(msg, Map.class);
                    for (Map map : maps) {
                        String stock = MapUtils.getString(map, "sym", "");
                        if (StringUtils.isBlank(stock) || !stockSet.contains(stock)) {
                            continue;
                        }

                        Double askPrice = MapUtils.getDouble(map, "ap");
                        Double bidPrice = MapUtils.getDouble(map, "bp");
                        Double tradePrice;
                        if (bidPrice == null) {
                            tradePrice = askPrice;
                        } else {
                            tradePrice = (askPrice + bidPrice) / 2;
                        }
                        if (tradePrice != null) {
                            realtimeQuoteForOptionMap.put(stock, tradePrice);
                        }
                    }
                    //                    long current = System.currentTimeMillis();
                    //                    if ((current / 1000) % 20 == 0) {
                    //                        log.info("quote for option price(time={}): {}", current, realtimeQuoteForOptionMap);
                    //                    }
                }

                // 退出前反订阅
                for (String stock : stockSet) {
                    sendMessage("{\"action\":\"unsubscribe\", \"params\":\"Q." + stock + "\"}");
                }
                log.info("unsubscribe real-time quote for option");
            } catch (Exception e) {
                log.info("getRealtimeQuoteForOption error. ", e);
            }
        });
        Thread.sleep(1000);
    }

    public void getQuoteForOption() throws InterruptedException {
        getQuoteForOption = true;

        executor.execute(() -> {
            try {
                while (getQuoteForOption) {
                    String msg = optionQuoteBQ.poll(1, TimeUnit.SECONDS);
                    if (StringUtils.isBlank(msg)) {
                        continue;
                    }

                    List<Map> maps = JSON.parseArray(msg, Map.class);
                    for (Map map : maps) {
                        String code = MapUtils.getString(map, "sym", "");
                        if (!StringUtils.startsWithIgnoreCase(code, "O:")) {
                            continue;
                        }

                        Double askPrice = MapUtils.getDouble(map, "ap");
                        Double bidPrice = MapUtils.getDouble(map, "bp");
                        int askVol = MapUtils.getInteger(map, "as", 0);
                        int bidVol = MapUtils.getInteger(map, "bs", 0);
                        if (askPrice != null && bidPrice != null && askPrice > 0 && bidPrice > 0 && askVol > 5 && bidVol > 5) {
                            double midPrice = BigDecimal.valueOf((bidPrice + askPrice) / 2).setScale(2, RoundingMode.UP).doubleValue();
                            log.info("polygon quote. code={}\tbidPrice={}\tbidVol={}\taskPrice={}\taskVol={}\tmidPrice={}", code, bidPrice, bidVol, askPrice, askVol, midPrice);
                        }
                    }
                    //                    long current = System.currentTimeMillis();
                    //                    if ((current / 1000) % 20 == 0) {
                    //                        log.info("quote for option price(time={}): {}", current, realtimeQuoteForOptionMap);
                    //                    }
                }

            } catch (Exception e) {
                log.info("getQuoteForOption error. ", e);
            }
        });
        log.info("start monitor polygon quote");
    }

    public boolean unsubscribe(String stock) {
        if (unsubcribeStockSet.contains(stock)) {
            return false;
        }
        executor.execute(() -> sendMessage("{\"action\":\"unsubscribe\", \"params\":\"T." + stock + "\"}"));
        unsubcribeStockSet.add(stock);
        if (unsubcribeStockSet.size() % 100 == 0) {
            log.info("unsubscribeStockSet size: " + unsubcribeStockSet.size() + " " + System.currentTimeMillis());
        }
        return true;
    }

    public void unsubscribeAll() {
        for (String stock : allStockSet) {
            unsubscribe(stock);
        }
        log.info("=========== finish unsubscribe ===========");
    }

    public void listenStopLoss(Map<String, StopLoss> stockToStopLoss) {
        if (MapUtils.isEmpty(stockToStopLoss)) {
            log.info("there is no stock need to listen stop loss");
            log.info("trade exit");
            //            System.exit(0);
            return;
        }
        listenStopLoss = true;
        unsubcribeStockSet.removeAll(stockToStopLoss.keySet());
        log.info("begin listen stop loss: " + stockToStopLoss);
        for (String stock : stockToStopLoss.keySet()) {
            sendMessage("{\"action\":\"subscribe\", \"params\":\"T." + stock + "\"}");
        }

        try {
            while (true) {
                String msg = stopLossBQ.poll(10, TimeUnit.SECONDS);
                // 如果没有监听数据，每十秒判断一次是否有持仓，可能已经在收盘前卖出了。没有持仓则清除监听，直至空可退出监听
                if (StringUtils.isBlank(msg)) {
                    Set<String> noNeedListen = Sets.newHashSet();
                    for (String stock : stockToStopLoss.keySet()) {
                        StockPosition position = tradeExecutor.getPosition(stock);
                        if (position == null || position.getCanSellQty() == 0) {
                            noNeedListen.add(stock);
                            unsubscribe(stock);
                        }
                    }
                    noNeedListen.forEach(s -> stockToStopLoss.remove(s));
                    if (stockToStopLoss.size() == 0) {
                        log.info("all stock has stop loss");
                        log.info("trade exit");
                        //                        System.exit(0);
                        return;
                    }
                    continue;
                }

                List<Map> maps = JSON.parseArray(msg, Map.class);
                for (Map map : maps) {
                    String stock = MapUtils.getString(map, "sym", "");
                    if (StringUtils.isBlank(stock)) {
                        continue;
                    }

                    Double price = MapUtils.getDouble(map, "p");
                    if (price == null) {
                        continue;
                    }

                    StopLoss stopLoss = stockToStopLoss.get(stock);
                    if (stopLoss == null) {
                        log.info(stock + " don't need stop loss. it'll be unsubscribe");
                        unsubscribe(stock);
                        continue;
                    }
                    Long time = MapUtils.getLong(map, "t");
                    if (time % 1000 == 0) {
                        log.info("time: {}, data: {}", time, map);
                    }
                    double lossPrice = stopLoss.getLossPrice();
                    double canSellQty = stopLoss.getCanSellQty();
                    if (price < lossPrice) {
                        double orderPrice = BigDecimal.valueOf(lossPrice * 0.99).setScale(2, BigDecimal.ROUND_FLOOR).doubleValue();
                        log.info(stock + " touch the stop loss. current price=" + price + ", lossPrice=" + lossPrice + ", orderPrice=" + orderPrice);
                        tradeExecutor.placeStopLossOrder(stock, canSellQty, orderPrice);
                        stockToStopLoss.remove(stock);
                    }
                }
                if (stockToStopLoss.size() == 0) {
                    log.info("listen stop loss end!");
                    log.info("trade exit");
                    //                    System.exit(0);
                    return;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            log.info("listenStopLoss error. " + e.getMessage());
        }
    }

    public void reconnect() {
        log.info("reconnect websocket");
        listenStopLoss = false;
        subscribed = false;
        reconnect = true;
        connect();
        boolean authSuccess = waitAuth();
        if (!authSuccess) {
            log.info("reconnect failed!");
            return;
        }
        log.info("reconnect finish");
        //        tradeExecutor.reListenStopLoss();
    }

    public void inputTestData() {
        executor.execute(() -> {
            Scanner scan = new Scanner(System.in);
            while (true) {
                System.out.println("input test data");
                String msg = scan.nextLine();
                System.out.println(msg);
                if (msg.startsWith("Q.")) {
                    realtimeQuoteBQ.offer(msg.substring(2));
                } else if (msg.startsWith("T.")) {
                    subscribeBQ.offer(msg.substring(2));
                }
                if (msg.equals("stop")) {
                    scan.close();
                    return;
                }
            }
        });
    }

    public static void main(String[] args) throws InterruptedException {
        long time = Date.from(LocalDateTime.now().plusDays(1).withHour(4).withMinute(59).withSecond(0).withNano(0).toInstant(ZoneOffset.of("+8"))).getTime();
        System.out.println(time);
        RealTimeDataWS_DB2_2 client = new RealTimeDataWS_DB2_2();
        client.init();
        //        client.sendToTradeDataListener();

        //        while (true) {
        //            Thread.sleep(1000);
        //        }
    }
}