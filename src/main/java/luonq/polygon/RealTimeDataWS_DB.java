package luonq.polygon;

import bean.BOLL;
import bean.FrontReinstatement;
import bean.Node;
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
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.eventbus.AsyncEventBus;
import lombok.extern.slf4j.Slf4j;
import luonq.data.ReadFromDB;
import luonq.execute.LoadOptionTradeData;
import luonq.execute.ReadWriteOptionTradeInfo;
import luonq.listener.OptionStockListener;
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
import java.net.URI;
import java.nio.ByteBuffer;
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
public class RealTimeDataWS_DB {

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
    public static Map<String, Double> realtimeQuoteMap = Maps.newHashMap();
    public static Map<String, Double> realtimeQuoteForOptionMap = Maps.newHashMap();
    private BlockingQueue<String> subscribeBQ = new LinkedBlockingQueue<>(1000);
    private BlockingQueue<String> stopLossBQ = new LinkedBlockingQueue<>(1000);
    private BlockingQueue<String> realtimeQuoteBQ = new LinkedBlockingQueue<>(1000);
    private ThreadPoolExecutor executor;
    private long preTradeTime;
    private long openTime;
    private long listenEndTime;
    private Date closeCheckTime;
    private boolean listenEnd = false;
    private NodeList list = new NodeList(10);
    private NodeList optionList = new NodeList(10);
    private AtomicBoolean hasAuth = new AtomicBoolean(false);
    private OptionStockListener optionStockListener = new OptionStockListener();

    public static Set<String> stockSet = Sets.newHashSet();
    public Set<String> allStockSet = Sets.newHashSet();
    private static Set<String> unsubcribeStockSet = Sets.newHashSet();
    private AsyncEventBus tradeEventBus;
    private Session userSession = null;
    private boolean testOption = false;

    private List<String> optionStockSet;
    private OptionTradeExecutor optionTradeExecutor;

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

            if (testOption || MapUtils.isEmpty(tradeExecutor.getAllPosition())) {
                initHistoricalData();
                initMessageListener();
                subcribeStock();
                //                inputTestData();
                sendToTradeDataListener();
                close();
                log.info("trade finish");
            }
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
        realtimeQuoteMap = Maps.newHashMap();
        subscribeBQ = new LinkedBlockingQueue<>(1000);
        stopLossBQ = new LinkedBlockingQueue<>(1000);
        realtimeQuoteBQ = new LinkedBlockingQueue<>(1000);
        listenEnd = false;
        list = new NodeList(10);
        hasAuth = new AtomicBoolean(false);
        unsubcribeStockSet = Sets.newHashSet();
        tradeEventBus = asyncEventBus();
        //        if (tradeExecutor == null) {
        //            tradeExecutor = new TradeExecutor_DB();
        //        }
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
            ReadWriteOptionTradeInfo.init();
            if (!testOption) {
                computeHisOverBollingerRatio();
                loadEarningInfo();
                stockSet = buildStockSet();
            }
            //            stockSet.add("AAPL"); // todo 测试用需删除
            optionStockSet = LoadOptionTradeData.earningStocks;
            // dn策略强制删除option涉及到的股票
            //            stockSet.removeAll(optionStockSet);

            allStockSet.addAll(stockSet);
            allStockSet.addAll(optionStockSet);
            //            stockSet.clear();
            //            stockSet.add("RNST");

            loadLastDn();
            loadLatestMA20();

            log.info("finish init historical data");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void initTrade() {
        try {
            tradeExecutor.setList(list);
            tradeExecutor.setClient(this);
            tradeExecutor.init();

            optionTradeExecutor = new OptionTradeExecutor();
            optionTradeExecutor.setClient(this);
            optionTradeExecutor.setOptionStockListener(optionStockListener);
            optionTradeExecutor.init();

            log.info("finish init trade");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void initMessageListener() {
        try {
            if (!testOption) {
                TradeDataListener_DB tradeDataListener = new TradeDataListener_DB();
                tradeDataListener.setStockSet(stockSet);
                tradeDataListener.setClient(this);
                tradeDataListener.setList(list);
                tradeEventBus.register(tradeDataListener);
            }

            tradeEventBus.register(optionStockListener);

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

    private void initManyTime() {
        LocalDateTime now = LocalDateTime.now();
        boolean beforeDawn = now.getHour() < 10; // 小于10则表示新的一天凌晨
        LocalDateTime closeCheck = now;
        LocalDateTime preTrade = now;
        if (!beforeDawn) {
            closeCheck = now.plusDays(1);
        }
        if (beforeDawn) {
            preTrade = now.minusDays(1);
        }

        int openHour, closeHour, preMin = 0 + DELAY_MINUTE, openMin = 1;
        if (now.isAfter(summerTime) && now.isBefore(winterTime)) {
            openHour = 22;
            closeHour = 3;
        } else {
            openHour = 22;
            closeHour = 4;
        }
        preTradeTime = preTrade.withHour(openHour).withMinute(preMin).withSecond(0).withNano(0).toInstant(ZoneOffset.of("+8")).toEpochMilli();
        openTime = now.withHour(openHour).withMinute(openMin).withSecond(0).withNano(0).toInstant(ZoneOffset.of("+8")).toEpochMilli();
        closeCheckTime = Date.from(closeCheck.withHour(closeHour).withMinute(59).withSecond(0).withNano(0).toInstant(ZoneOffset.of("+8")));
        listenEndTime = openTime + LISTENING_TIME;
        log.info("finish initialize many time. preTradeTime=" + preTradeTime + ", openTime=" + openTime + ", closeCheckTime=" + closeCheckTime);
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
        log.info("closing websocket. reason=" + reason);
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
                    //                    // 当前价大于前一天的下轨则直接过滤
                    //                    Double lastDn = stockToLastDn.get(stock);
                    //                    if (lastDn == null || price > lastDn || price < PRICE_LIMIT) {
                    //                        unsubscribe(stock);
                    //                        continue;
                    //                    }

                    log.info("receive data: {}", msg);
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
        getRealtimeQuote();
        getRealtimeQuoteForOption();
        ReadWriteOptionTradeInfo.writeStockOpenPrice();
        tradeExecutor.beginTrade();
        optionTradeExecutor.beginTrade();
    }

    // 成交前获取实时报价
    public void getRealtimeQuote() throws InterruptedException {
        getRealtimeQuote = true;
        Set<String> stockSet = list.getNodes().stream().map(Node::getName).collect(Collectors.toSet());
        if (CollectionUtils.isEmpty(stockSet)) {
            log.info("there is no stock to get real-time quote");
            return;
        }
        log.info("get real-time quote: " + stockSet);
        for (String stock : stockSet) {
            sendMessage("{\"action\":\"subscribe\", \"params\":\"Q." + stock + "\"}");
        }

        executor.execute(() -> {
            try {
                while (getRealtimeQuote) {
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
                            realtimeQuoteMap.put(stock, tradePrice);
                        }
                    }
                    long current = System.currentTimeMillis();
                    if ((current / 1000) % 1 == 0) {
                        log.info("quote price(time={}): {}", current, realtimeQuoteMap);
                    }
                }

                // 退出前反订阅
                for (String stock : stockSet) {
                    sendMessage("{\"action\":\"unsubscribe\", \"params\":\"Q." + stock + "\"}");
                }
                log.info("unsubscribe real-time quote");
            } catch (Exception e) {
                log.info("getRealtimeQuote error. " + e.getMessage());
            }
        });
        Thread.sleep(1000);
    }

    public void getRealtimeQuoteForOption() throws InterruptedException {
        getRealtimeQuoteForOption = true;
        Set<String> stockSet = optionStockListener.getCanTradeStocks();
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
                    long current = System.currentTimeMillis();
                    if ((current / 1000) % 10 == 0) {
                        log.info("quote for option price(time={}): {}", current, realtimeQuoteForOptionMap);
                    }
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
        tradeExecutor.reListenStopLoss();
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
        RealTimeDataWS_DB client = new RealTimeDataWS_DB();
        client.init();
        //        client.sendToTradeDataListener();

        //        while (true) {
        //            Thread.sleep(1000);
        //        }
    }
}