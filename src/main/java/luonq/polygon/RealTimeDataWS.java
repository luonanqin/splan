package luonq.polygon;

import bean.BOLL;
import bean.EarningDate;
import bean.FrontReinstatement;
import bean.Node;
import bean.NodeList;
import bean.RatioBean;
import bean.SplitStockInfo;
import bean.StockEvent;
import bean.StockKLine;
import bean.StockPosition;
import bean.StockRatio;
import bean.StopLoss;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.eventbus.AsyncEventBus;
import luonq.strategy.execute.OverBollingerDn;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
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
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import static util.Constants.SEPARATOR;

//@Component
@ClientEndpoint
public class RealTimeDataWS {

    public static final String TEST_STOCK = "";
    public static final String TEST_SUBSCRIBE_STOCK = "T.CXAI,T.SLV,T.DRMA,T.FMS,T.SOXS,T.GFAI,T.APPH,T.TNA,T.IWM,T.TZA,T.TWM,T.SRTY,T.NFLX,T.JEPI,T.META,T.AUD,T.IONQ,T.TIVC,T.TSLA,T.CYTO,T.FFIE,T.GPRO,T.TMF,T.MO,T.BBIG,T.SOXL,T.BBLN,T.MINM,T.MSFT,T.UVXY,T.GOOG,T.MVST,T.NVDA,T.TQQQ,T.GETR,T.ATNF,T.IBRX,T.AMZN,T.PLX,T.UPRO,T.UCO,T.PACW,T.SPY,T.MARA,T.AGBA,T.ZFOX,T.INTC,T.DIS,T.PYPL,T.CDTX,T.INBS,T.LICN,T.SPXU,T.SRTS,T.SBFM,T.TSLL,T.NRGU,T.SOFI,T.SQQQ,T.AIG,T.U,T.O,T.EGIO,T.MTC,T.IEF,T.TLT,T.CWEB,T.TCBP,T.SVFD,T.GDC,T.TRKA,T.AAPL,T.SSO,T.IDEX,T.VRM,T.SNDL,T.NTEST,T.GMDA,T.ABNB,T.TMV,T.SDOW,T.VCIG,T.XLI,T.CRKN,T.GDXD,T.VSSYW,T.KNTE,T.ASML,T.NKLA,T.AMD,T.OPK,T.UVIX,T.SBSW,T.JD,T.LABU,T.BABA,T.PTGX,T.CS,T.BITO,T.NIO,T.TCRX,T.XPEV,T.SFWL,T.OPEN,T.BNTX,T.GOOGL,T.INDA,T.ZURA,T.LILM,T.PBTS,T.ADMP,T.Z,T.RYAAY,T.WMT,T.CISO,T.USO,T.CCL,T.BOIL,T.TUP,T.AFRM,T.SLGG,T.OPFI,T.BNKU,T.F,T.BE,T.SURG,T.MWG,T.LCID,T.RIVN,T.NVO,T.SOS,T.CNEY,T.SNTG,T.POLA,T.IZM,T.OXY,T.QBTS,T.GFI,T.GOEV,T.EPAM,T.HOUR,T.BLBD,T.GOLD,T.JAGX,T.GLD,T.TWLO,T.EURN,T.JOBY,T.CVX,T.VIXY,T.ALLR,T.SMX,T.MRNA,T.HYZN,T.BFRG,T.ZIM,T.NOBL,T.ORGO,T.VATE,T.IAU,T.SCHD,T.AGQ,T.VOO,T.XLF,T.SOUN,T.GETY,T.UAVS,T.CRSP,T.KO,T.PTEST,T.CVNA,T.CI,T.KOLD,T.WFC,T.BLUE,T.CJJD,T.GDX,T.LABD,T.BLNK,T.BAC,T.MULN,T.LUNR,T.IMGN,T.IONM,T.FUTU,T.SHFS,T.SONO,T.STSS,T.CEI,T.CMCSA,T.CYTK,T.HLN,T.IREN,T.SBUX,T.ZOM,T.TCJH,T.C,T.QQQ,T.TOP,T.UFAB,T.RACE,T.HKD,T.AMPE,T.QQQM,T.DDL,T.YINN,T.ASTI,T.UBS,T.TCMD,T.FNGU,T.SPXS,T.LAC,T.PLUG,T.TRVN,T.QCOM,T.NVTA,T.JEPQ,T.ICUCW,T.BULZ,T.BRDS,T.GRVY,T.CLRO,T.SHOP,T.MOBQ,T.LI,T.VALE,T.GCTK,T.BB,T.DRN,T.HUBS,T.DNA,T.IQ,T.PLTR,T.FSLR,T.QLD,T.PAAS,T.PBLA,T.BIOL,T.HSBC,T.PEP,T.AUGX,T.MFH,T.CPE,T.DOG,T.HUDI,T.COSM,T.WDAY,T.XELA,T.DB,T.INPX,T.SNN,T.PDD,T.FXI,T.SSRM,T.TSLQ,T.HMPT,T.BYND,T.QID,T.GRIL,T.GSK,T.BNKD,T.HSTO,T.CTLT,T.ZION,T.HCDI,T.WETG,T.VXX,T.SAVA,T.SVXY,T.AKAN,T.BFLY,T.CARR,T.CLSK,T.IBM,T.DWAC,T.NKE,T.NVDS,T.INCR,T.MSTR,T.T,T.TSM,T.CSCO,T.DADA,T.SQ,T.ZSL,T.SARK,T.IVV,T.SDS,T.SPLG,T.UDOW,T.HMY,T.DXF,T.PFE,T.OMH,T.MHNC,T.SIGA,T.ATXG,T.NOGN,T.LYFT,T.RIOT,T.BMY,T.FAS,T.TECL,T.SPG,T.NDAQ,T.GNS,T.SPXL,T.WAL,T.SVIX,T.UNG,T.NOK,T.SAI,T.ARKK,T.AKLI,T.RKLB,T.AGFY,T.DDOG,T.RBLX,T.NBTX,T.MCRB,T.SNAP,T.TIO,T.HGEN,T.TAL,T.WOOF,T.DIA,T.BTBT,T.MGAM,T.UPWK,T.TWOU,T.AMC,T.YANG,T.WISA,T.ABBV,T.TCOM,T.SMTC,T.MAXN,T.UPST,T.CMND,T.ARR,T.ROKU,T.FISV,T.TSLS,T.MU,T.RELI,T.AI,T.AZN,T.NU,T.ZVZZT,T.TLRY,T.BURU,T.XLP,T.EEFT,T.CROX,T.ADXN,T.PBR,T.CXAIW,T.BITI,T.APE,T.ABB,T.FNGD,T.XLV,T.SENS,T.APLD,T.HOLO,T.TSP,T.HUIZ,T.ATEST,T.NVS,T.TLTW,T.MOS,T.DPST,T.EJH,T.BPT,T.SHEL,T.SPRO,T.SVOL,T.COIN,T.NCMI,T.TMC,T.MGIH,T.DHY,T.FOXA,T.TRUP,T.WPM,T.NVAX,T.SQM,T.KWEB,T.ZH,T.EVA,T.SNGX,T.LICY,T.HIMX,T.SPYG,T.RWM,T.VXRT,T.CTVA,T.MS,T.SPCE,T.ADBE,T.ING,T.FLNC,T.TDOC,T.NNOX,T.ENPH,T.NCLH,T.MCD,T.GDXU";
    public static final Set<String> invalidStockSet = Sets.newHashSet("FRC", "SIVB", "BIOR", "HALL", "OBLG", "ALBT", "IPDN", "OPGN", "TENX", "AYTU", "DAVE", "NXTP", "ATHE", "CANF", "GHSI", "EEMX", "EFAX", "HYMB", "NYC", "SPYX", "PBLA", "JEF", "ACGN", "EAR", "FWBI", "IDRA", "JFU", "CNET", "APM", "JAGX", "OCSL", "OGEN", "SIEN", "SRKZG", "CETX", "UVIX", "EDBL", "PHIO", "SWVL", "MRKR", "REED", "WISA", "FTFT", "FVCB", "LMNL", "REVB", "DYNT", "BRSF", "LCI", "DGLY", "PCAR", "CZOO", "MIGI", "NAOV", "COMS", "GFAI", "INBS", "SNGX", "APRE", "FNGG", "GNUS", "VYNE", "CRBP", "ATNX", "CFRX", "ECOR", "NVDEF", "SHIP", "AMST", "GMBL", "RELI", "WINT", "FNRN", "MFH", "XBRAF", "RKDA", "HCDI", "IONM", "VXX", "SFT", "VEON", "AKAN", "NYMT", "ORTX", "ASLN", "KRBP", "IVOG", "IVOO", "IVOV", "VIOG", "VIOO", "VIOV", "GRAY", "MRBK", "BAOS", "GGB", "LKCO", "TESTING", "VIA", "IDAI", "PTIX", "RDHL", "CUEN", "FRGT", "GCBC", "ALLR", "CREX", "MTP", "MNST", "NOGN", "BPTS", "CETXP", "ENSC", "HLBZ", "CHNR", "BEST", "MBIO", "WTER", "AGRX", "BLBX", "VBIV", "WISH", "EJH", "ARVL", "MEIP", "MINM", "ASNS", "VERB", "BKTI", "FRSX", "OIG", "LGMK", "POAI", "SMFL", "CLXT", "JXJT", "SBET", "EZFL", "IMPP", "MEME", "PSTV", "VISL", "WEED", "MDRR", "MULN", "WGS", "GTE", "SMH", "CRESY", "BBIG", "HEPA", "AWH", "FRLN", "LPCN", "RETO", "VERO", "ALPP", "BNMV", "EAST", "GLMD", "IFBD", "RETO", "XBIO", "XELA", "XELAP", "CYCN", "GREE", "SDIG", "BIOC", "AULT", "NISN", "CHDN", "LGMK", "HLBZ", "LPCN", "BBIG", "XBIO", "JATT", "TGAA", "GRAY", "GREE", "SDIG", "SMFL", "SMFG", "VERO", "LCI", "TYDE", "DRMA", "BLIN", "HEPA", "SESN", "CR", "LITM", "SNGX", "GE", "MULN", "CGNX", "ML", "MDRR", "PR", "VAL", "EBF", "MTP", "CYCN", "XELA", "ENVX", "EQT", "GLMD", "DCFC", "POAI", "BNOX", "FRLN", "CINC", "NISN", "REFR", "CAPR", "SYRS", "ALPP", "RETO", "VISL", "GNLN", "JXJT", "SAFE", "EZFL", "IDRA", "CRESY", "IMPP", "ZEV", "EAST", "BIOC", "IFBD", "STAR", "AWH", "TNXP", "WORX", "VLON", "PSTV", "SFT", "AGRX", "MBIO", "APRE", "GAME", "VERB", "CFRX", "BLBX", "COMS", "RKDA", "WISH", "NXTP", "TR", "ARVL", "EJH", "MEIP", "ENSC", "NYMT", "PNTM", "ASNS", "AKAN", "RDFN", "GMBL", "VYNE", "MNST", "LCAA", "FRSX", "CRBP", "ATNX", "OIG", "REED", "OUST", "ALLR", "NAOV", "KRBP", "ICMB", "XOS", "GFAI", "GNUS", "BGXX", "FTFT", "AMST", "FCUV", "VBIV", "BIIB", "MINM", "CLXT", "DGLY", "MRKR");
    public static final URI uri = URI.create("wss://socket.polygon.io/stocks");
    public static final double HIT = 0.5d; // 策略成功率
    public static final int PRICE_LIMIT = 7; // 价格限制，用于限制这个价格下的股票不参与计算
    public static final double LOSS_RATIO = 0.07d; // 止损比例
    public static final int DELAY_MINUTE = 0;
    public static final long LISTENING_TIME = 30000L; // 监听时长，毫秒
    private static LocalDateTime summerTime = BaseUtils.getSummerTime(null);
    private static LocalDateTime winterTime = BaseUtils.getWinterTime(null);

    private boolean subscribed = false;
    private boolean listenStopLoss = false;
    private boolean reconnect = false;
    private boolean manualClose = false;
    public static Map<String, Double> stockToLastDn = Maps.newHashMap();
    public static Map<String, Double> stockToM19CloseSum = Maps.newHashMap();
    public static Map<String, List<Double>> stockToM19Close = Maps.newHashMap();
    public static Map<String, StockRatio> originRatioMap;
    public static Set<String> todayEarningStockSet = Sets.newHashSet();
    public static Set<String> lastEarningStockSet = Sets.newHashSet();
    public static boolean getRealtimeQuote = false;
    public static Map<String, Double> realtimeQuoteMap = Maps.newHashMap();
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
    private AtomicBoolean hasAuth = new AtomicBoolean(false);

    public static Set<String> stockSet;
    private static Set<String> unsubcribeStockSet = Sets.newHashSet();
    private static Map<String, String> fileMap;
    private AsyncEventBus tradeEventBus;
    private Session userSession = null;

    @Autowired
    private TradeExecutor tradeExecutor;

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

            if (MapUtils.isEmpty(tradeExecutor.getAllPosition())) {
                initHistoricalData();
                subcribeStock();
                sendToTradeDataListener();
                close();
                System.out.println("trade finish");
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
        if (tradeExecutor == null) {
            tradeExecutor = new TradeExecutor();
        }
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
                System.out.println("auth failed");
                //                System.exit(0);
                return false;
            }
        }
    }

    public void initHistoricalData() {
        try {
            String mergePath = Constants.HIS_BASE_PATH + "merge" + SEPARATOR;
            fileMap = BaseUtils.getFileMap(mergePath);
            originRatioMap = OverBollingerDn.computeHistoricalOverBollingerRatio();
            loadEarningInfo();
            stockSet = buildStockSet(fileMap);
            //            stockSet.clear();
            //            stockSet.add("RNST");

            loadLastDn();
            loadLatestMA20();

            System.out.println("finish init historical data");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void initTrade() {
        try {
            TradeDataListener tradeDataListener = new TradeDataListener();
            tradeDataListener.setClient(this);
            tradeDataListener.setList(list);
            tradeEventBus.register(tradeDataListener);

            tradeExecutor.setList(list);
            tradeExecutor.setClient(this);
            System.out.println("finish init trade");
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

    public void listenExistPosition() {
        Map<String, StockPosition> allPosition = tradeExecutor.getAllPosition();
        tradeExecutor.setTradeStock(Lists.newArrayList(allPosition.keySet()));
        tradeExecutor.closeCheckPosition();
        if (!tradeExecutor.isRealTrade()) {
            tradeExecutor.reListenStopLoss();
        }
    }

    public Date getCloseCheckTime() {
        return closeCheckTime;
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

        if (now.isAfter(summerTime) && now.isBefore(winterTime)) {
            preTradeTime = preTrade.withHour(21).withMinute(28 + DELAY_MINUTE).withSecond(0).withNano(0).toInstant(ZoneOffset.of("+8")).toEpochMilli();
            openTime = now.withHour(21).withMinute(30).withSecond(0).withNano(0).toInstant(ZoneOffset.of("+8")).toEpochMilli();
            closeCheckTime = Date.from(closeCheck.withHour(3).withMinute(59).withSecond(0).withNano(0).toInstant(ZoneOffset.of("+8")));
        } else {
            preTradeTime = preTrade.withHour(22).withMinute(28 + DELAY_MINUTE).withSecond(0).withNano(0).toInstant(ZoneOffset.of("+8")).toEpochMilli();
            openTime = now.withHour(22).withMinute(30).withSecond(0).withNano(0).toInstant(ZoneOffset.of("+8")).toEpochMilli();
            closeCheckTime = Date.from(closeCheck.withHour(4).withMinute(59).withSecond(0).withNano(0).toInstant(ZoneOffset.of("+8")));
        }
        listenEndTime = openTime + LISTENING_TIME;
        System.out.println("finish initialize many time. preTradeTime=" + preTradeTime + ", openTime=" + openTime + ", closeCheckTime=" + closeCheckTime);
    }

    private void initThreadExecutor() {
        int threadCount = 2000;
        int corePoolSize = threadCount;
        int maximumPoolSize = corePoolSize;
        long keepAliveTime = 60L;
        LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();
        executor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.MINUTES, workQueue);
    }

    public static void loadEarningInfo() throws Exception {
        LocalDate now = LocalDate.now();
        String todayDate = now.format(Constants.FORMATTER);
        List<StockKLine> kLines = BaseUtils.loadDataToKline(Constants.HIS_BASE_PATH + "merge/AAPL", 2024, 2023);
        String lastDate = kLines.get(0).getDate();

        Map<String, List<EarningDate>> earningDateMap = BaseUtils.getEarningDate(null);
        List<EarningDate> earningDates = MapUtils.getObject(earningDateMap, todayDate, Lists.newArrayList());
        todayEarningStockSet = earningDates.stream().map(EarningDate::getStock).collect(Collectors.toSet());

        List<EarningDate> lastEarningDates = MapUtils.getObject(earningDateMap, lastDate, Lists.newArrayList());
        lastEarningStockSet = lastEarningDates.stream().map(EarningDate::getStock).collect(Collectors.toSet());
    }

    private Set<String> buildStockSet(Map<String, String> fileMap) throws Exception {
        LocalDate now = LocalDate.now();
        LocalDate standard = now.minusDays(4);
        // 过滤前日收盘价低于CLOSE_PRICE
        Set<String> set = Sets.newHashSet();
        for (String stock : fileMap.keySet()) {
            String filePath = fileMap.get(stock);
            StockKLine first = BaseUtils.getLatestKLine(filePath);
            double close = first.getClose();
            double open = first.getOpen();
            double volume = first.getVolume().doubleValue();
            String date = first.getDate();
            LocalDate parse = LocalDate.parse(date, Constants.FORMATTER);
            if (parse.isBefore(standard)) {
                continue;
            }
            if (close > PRICE_LIMIT && volume > 100000 && close <= open) {
                set.add(stock);
            }
        }
        System.out.println(String.format("stock latest close great %d size: %d", PRICE_LIMIT, set.size()));

        // 过滤所有OverBolling策略命中率低于HIT
        for (String stock : originRatioMap.keySet()) {
            StockRatio stockRatio = originRatioMap.get(stock);
            Map<Integer, RatioBean> ratioMap = stockRatio.getRatioMap();
            boolean hitFailed = true;
            for (RatioBean ratio : ratioMap.values()) {
                double ratioVal = ratio.getRatio();
                if (ratioVal > HIT) {
                    hitFailed = false;
                }
            }
            if (hitFailed) {
                set.remove(stock);
            }
        }
        System.out.println(String.format("stock overbolling hit great %f size: %d", HIT, set.size()));

        // 过滤所有合股
        Set<String> mergeStock = BaseUtils.getMergeStock();
        set.removeAll(mergeStock);
        System.out.println(String.format("filter merge stock, the stock set size is %d", set.size()));

        // 过滤所有拆股
        Set<SplitStockInfo> splitStockInfo = BaseUtils.getSplitStockInfo();
        Set<String> splitStock = splitStockInfo.stream().map(SplitStockInfo::getStock).collect(Collectors.toSet());
        set.removeAll(splitStock);
        System.out.println(String.format("filter split stock, the stock set size is %d", set.size()));

        // 过滤所有今年前复权因子低于0.98的
        LocalDate firstDay = LocalDate.parse("2023-01-01", DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        Set<FrontReinstatement> reinstatementInfo = BaseUtils.getFrontReinstatementInfo();
        Map<String, FrontReinstatement> map = reinstatementInfo.stream().collect(Collectors.toMap(FrontReinstatement::getStock, Function.identity()));
        for (String stock : map.keySet()) {
            FrontReinstatement fr = map.get(stock);
            double factor = fr.getFactor();
            if (factor > 0.98) {
                continue;
            }

            String date = fr.getDate();
            LocalDate dateParse = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            if (dateParse.isAfter(firstDay)) {
                set.remove(stock);
            }
        }
        System.out.println(String.format("filter front reinstatement less 0.98 stock, the stock set size is %d", set.size()));

        // 财报当天和财报后一天都不进行交易
        set.removeAll(todayEarningStockSet);
        set.removeAll(lastEarningStockSet);
        System.out.println(String.format("filter earning and lastEarning stock, the stock set size is %d", set.size()));

        // 历史搜集的无效股票
        set.removeAll(invalidStockSet);

        System.out.println("after filte: " + set);
        return set;
    }

    public static void loadLatestMA20() throws Exception {
        int beforeYear = 2024, afterYear = 2022;
        for (String stock : stockSet) {
            if (StringUtils.isNotBlank(TEST_STOCK) && !stock.equals(TEST_STOCK)) {
                continue;
            }
            List<StockKLine> kLines = BaseUtils.loadDataToKline(Constants.HIS_BASE_PATH + "merge" + SEPARATOR + stock, beforeYear, afterYear);
            if (kLines.size() < 19) {
                continue;
            }
            //            kLines = kLines.subList(1, kLines.size());
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
        System.out.println("finish load lastest 19-days close data");
    }

    private static void loadLastDn() throws Exception {
        for (String stock : stockSet) {
            if (StringUtils.isNotBlank(TEST_STOCK) && !stock.equals(TEST_STOCK)) {
                continue;
            }
            List<BOLL> bolls = BaseUtils.readBollFile(Constants.HIS_BASE_PATH + "mergeBoll" + SEPARATOR + stock, 2024, 2023);
            if (CollectionUtils.isEmpty(bolls)) {
                continue;
            }
            BOLL boll = bolls.get(0);
            double dn = boll.getDn();
            stockToLastDn.put(stock, dn);
        }
        System.out.println("finish load last DN for BOLL");
    }

    public AsyncEventBus asyncEventBus() {
//        int corePoolSize = Runtime.getRuntime().availableProcessors();
//        int maxPoolSize = 100;
//        int keepAliveTime = 60 * 1000;

        //        BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(2000);
        //        RejectedExecutionHandler handler = new ThreadPoolExecutor.AbortPolicy();
        //        ThreadFactory factory = new ThreadFactory() {
        //            private final AtomicInteger integer = new AtomicInteger(1);
        //
        //            @Override
        //            public Thread newThread(Runnable r) {
        //                return new Thread(r, "TheadPool-Thread-" + integer.getAndIncrement());
        //            }
        //        };

        //        return new AsyncEventBus(new ThreadPoolExecutor(corePoolSize, maxPoolSize, keepAliveTime, TimeUnit.SECONDS, workQueue, factory, handler));
        return new AsyncEventBus(executor);
    }

    /**
     * Callback hook for Connection open events.
     *
     * @param userSession the userSession which is opened.
     */
    @OnOpen
    public void onOpen(Session userSession) {
        System.out.println("opening websocket");
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
        System.out.println("closing websocket. reason=" + reason);
        try {
            Thread.sleep(10000);
        } catch (InterruptedException e) {
        }
        if (manualClose) {
            System.out.println("manual close websocket");
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
                //                System.out.println(message);
                subscribeBQ.offer(message);
            } else if (listenStopLoss) {
                stopLossBQ.offer(message);
            } else if (getRealtimeQuote) {
                realtimeQuoteBQ.offer(message);
            } else {
                List<Map> maps = JSON.parseArray(message, Map.class);
                Map map = maps.get(0);
                String status = MapUtils.getString(map, "status");
                String msg = MapUtils.getString(map, "message");

                if ("connected".equals(status) && "Connected Successfully".equals(msg)) {
                    System.out.println(status);
                    sendMessage("{\"action\":\"auth\",\"params\":\"Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY\"}");
                } else if ("auth_success".equals(status) && "authenticated".equals(msg)) {
                    System.out.println(status);
                    hasAuth.set(true);
                }
            }
        } catch (
          Exception e) {
            System.out.println("onMessage " + e.getMessage());
        }
    }

    private void subcribeStock() throws InterruptedException {
        subscribed = true;
        while (true) {
            LocalDateTime now = LocalDateTime.now();
            long nowTime = now.toInstant(ZoneOffset.of("+8")).toEpochMilli();
            if (nowTime < preTradeTime) {
                System.out.println("wait pre trade time. now is " + now.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                try {
                    Thread.sleep(5000);
                } catch (InterruptedException e) {
                    throw e;
                }
            } else {
                System.out.println("begin subcribe stock at " + now.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                break;
            }
        }

        executor.execute(() -> {
            for (String stock : stockSet) {
                sendMessage("{\"action\":\"subscribe\", \"params\":\"T." + stock + "\"}");
            }
            System.out.println("finish subcribe real time!");
        });
    }

    @OnMessage
    public void onMessage(ByteBuffer bytes) {
        System.out.println("Handle byte buffer");
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
                    } else if (stockSet.size() - unsubcribeStockSet.size() < 100) {
                        if (listenEndTime - currentTime > 10000) {
                            System.out.println("many stock has received data. keep listening");
                            continue;
                        }
                        beginTrade("many stock has received data. listen end!");
                        return;
                    } else {
                        System.out.println("there is no msg. continue");
                        continue;
                    }
                }

                //                System.out.println(msg);
                List<Map> maps = JSON.parseArray(msg, Map.class);
                Map<String, StockEvent> stockToEvent = Maps.newHashMap();
                for (Map map : maps) {
                    String stock = MapUtils.getString(map, "sym", "");

                    if (unsubcribeStockSet.contains(stock) || StringUtils.isBlank(stock)) {
                        //                        System.out.println(msg);
                        continue;
                    }
                    Double price = MapUtils.getDouble(map, "p");
                    Long time = MapUtils.getLong(map, "t");
                    //                    System.out.println(map);
                    if (time < openTime) {
                        if (time % 1000 == 0) {
                            System.out.println("time is early. " + map);
                            System.out.println(map);
                        }
                        continue;
                    }
                    if (time > listenEndTime) {
                        beginTrade(stock + " time is " + time + ", price is " + price + ".listen end!");
                        return;
                    }
                    // 当前价大于前一天的下轨则直接过滤
                    Double lastDn = stockToLastDn.get(stock);
                    if (lastDn == null || price > lastDn || price < PRICE_LIMIT) {
                        unsubscribe(stock);
                        continue;
                    }

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
                if (stockSet.size() == unsubcribeStockSet.size()) {
                    beginTrade("receive all open price! start trade!");
                    return;
                }
            }
        } catch (Exception e) {
            System.out.println("sendToTradeDataListener error. " + e.getMessage());
        }
    }

    public void beginTrade(String msg) throws Exception {
        subscribed = false;
        System.out.println(msg);
        unsubscribeAll();
        listenEnd = true;
        getRealtimeQuote();
        tradeExecutor.beginTrade();
    }

    // 成交前获取实时报价
    public void getRealtimeQuote() throws InterruptedException {
        getRealtimeQuote = true;
        Set<String> stockSet = list.getNodes().stream().map(Node::getName).collect(Collectors.toSet());
        System.out.println("get real-time quote: " + stockSet);
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

                        Double price = MapUtils.getDouble(map, "ap");
                        realtimeQuoteMap.put(stock, price);
                    }
                    System.out.println("quote price(time=" + System.currentTimeMillis() + "): " + realtimeQuoteMap);
                }

                // 退出前反订阅
                for (String stock : stockSet) {
                    sendMessage("{\"action\":\"unsubscribe\", \"params\":\"Q." + stock + "\"}");
                }
                System.out.println("unsubscribe real-time quote");
            } catch (Exception e) {
                System.out.println("getRealtimeQuote error. " + e.getMessage());
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
            System.out.println("unsubscribeStockSet size: " + unsubcribeStockSet.size() + " " + System.currentTimeMillis());
        }
        return true;
    }

    public void unsubscribeAll() {
        for (String stock : stockSet) {
            unsubscribe(stock);
        }
        System.out.println("=========== finish unsubscribe ===========");
    }

    public void listenStopLoss(Map<String, StopLoss> stockToStopLoss) {
        if (MapUtils.isEmpty(stockToStopLoss)) {
            System.out.println("there is no stock need to listen stop loss");
            System.out.println("trade exit");
            //            System.exit(0);
            return;
        }
        listenStopLoss = true;
        unsubcribeStockSet.removeAll(stockToStopLoss.keySet());
        System.out.println("begin listen stop loss: " + stockToStopLoss);
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
                        System.out.println("all stock has stop loss");
                        System.out.println("trade exit");
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
                        System.out.println(stock + " don't need stop loss. it'll be unsubscribe");
                        unsubscribe(stock);
                        continue;
                    }
                    Long time = MapUtils.getLong(map, "t");
                    if (time % 1000 == 0) {
                        System.out.println(map);
                    }
                    double lossPrice = stopLoss.getLossPrice();
                    double canSellQty = stopLoss.getCanSellQty();
                    if (price < lossPrice) {
                        double orderPrice = BigDecimal.valueOf(lossPrice * 0.99).setScale(2, BigDecimal.ROUND_FLOOR).doubleValue();
                        System.out.println(stock + " touch the stop loss. current price=" + price + ", lossPrice=" + lossPrice + ", orderPrice=" + orderPrice);
                        tradeExecutor.placeStopLossOrder(stock, canSellQty, orderPrice);
                        stockToStopLoss.remove(stock);
                    }
                }
                if (stockToStopLoss.size() == 0) {
                    System.out.println("listen stop loss end!");
                    System.out.println("trade exit");
                    //                    System.exit(0);
                    return;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println("listenStopLoss error. " + e.getMessage());
        }
    }

    public void reconnect() {
        System.out.println("reconnect websocket");
        listenStopLoss = false;
        subscribed = false;
        reconnect = true;
        connect();
        boolean authSuccess = waitAuth();
        if (!authSuccess) {
            System.out.println("reconnect failed!");
            return;
        }
        System.out.println("reconnect finish");
        tradeExecutor.reListenStopLoss();
    }

    public static void main(String[] args) throws InterruptedException {
        RealTimeDataWS client = new RealTimeDataWS();
        client.init();
    }
}