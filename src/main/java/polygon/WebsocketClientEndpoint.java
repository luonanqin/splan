package polygon;

import bean.BOLL;
import bean.NodeList;
import bean.StockKLine;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.eventbus.AsyncEventBus;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import strategy.OverBollingerDN2023OpenFirst;
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
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@ClientEndpoint
public class WebsocketClientEndpoint {

    public static final String TEST_STOCK = "";
    public static final String TEST_SUBSCRIBE_STOCK = "T.CXAI,T.SLV,T.DRMA,T.FMS,T.SOXS,T.GFAI,T.APPH,T.TNA,T.IWM,T.TZA,T.TWM,T.SRTY,T.NFLX,T.JEPI,T.META,T.AUD,T.IONQ,T.TIVC,T.TSLA,T.CYTO,T.FFIE,T.GPRO,T.TMF,T.MO,T.BBIG,T.SOXL,T.BBLN,T.MINM,T.MSFT,T.UVXY,T.GOOG,T.MVST,T.NVDA,T.TQQQ,T.GETR,T.ATNF,T.IBRX,T.AMZN,T.PLX,T.UPRO,T.UCO,T.PACW,T.SPY,T.MARA,T.AGBA,T.ZFOX,T.INTC,T.DIS,T.PYPL,T.CDTX,T.INBS,T.LICN,T.SPXU,T.SRTS,T.SBFM,T.TSLL,T.NRGU,T.SOFI,T.SQQQ,T.AIG,T.U,T.O,T.EGIO,T.MTC,T.IEF,T.TLT,T.CWEB,T.TCBP,T.SVFD,T.GDC,T.TRKA,T.AAPL,T.SSO,T.IDEX,T.VRM,T.SNDL,T.NTEST,T.GMDA,T.ABNB,T.TMV,T.SDOW,T.VCIG,T.XLI,T.CRKN,T.GDXD,T.VSSYW,T.KNTE,T.ASML,T.NKLA,T.AMD,T.OPK,T.UVIX,T.SBSW,T.JD,T.LABU,T.BABA,T.PTGX,T.CS,T.BITO,T.NIO,T.TCRX,T.XPEV,T.SFWL,T.OPEN,T.BNTX,T.GOOGL,T.INDA,T.ZURA,T.LILM,T.PBTS,T.ADMP,T.Z,T.RYAAY,T.WMT,T.CISO,T.USO,T.CCL,T.BOIL,T.TUP,T.AFRM,T.SLGG,T.OPFI,T.BNKU,T.F,T.BE,T.SURG,T.MWG,T.LCID,T.RIVN,T.NVO,T.SOS,T.CNEY,T.SNTG,T.POLA,T.IZM,T.OXY,T.QBTS,T.GFI,T.GOEV,T.EPAM,T.HOUR,T.BLBD,T.GOLD,T.JAGX,T.GLD,T.TWLO,T.EURN,T.JOBY,T.CVX,T.VIXY,T.ALLR,T.SMX,T.MRNA,T.HYZN,T.BFRG,T.ZIM,T.NOBL,T.ORGO,T.VATE,T.IAU,T.SCHD,T.AGQ,T.VOO,T.XLF,T.SOUN,T.GETY,T.UAVS,T.CRSP,T.KO,T.PTEST,T.CVNA,T.CI,T.KOLD,T.WFC,T.BLUE,T.CJJD,T.GDX,T.LABD,T.BLNK,T.BAC,T.MULN,T.LUNR,T.IMGN,T.IONM,T.FUTU,T.SHFS,T.SONO,T.STSS,T.CEI,T.CMCSA,T.CYTK,T.HLN,T.IREN,T.SBUX,T.ZOM,T.TCJH,T.C,T.QQQ,T.TOP,T.UFAB,T.RACE,T.HKD,T.AMPE,T.QQQM,T.DDL,T.YINN,T.ASTI,T.UBS,T.TCMD,T.FNGU,T.SPXS,T.LAC,T.PLUG,T.TRVN,T.QCOM,T.NVTA,T.JEPQ,T.ICUCW,T.BULZ,T.BRDS,T.GRVY,T.CLRO,T.SHOP,T.MOBQ,T.LI,T.VALE,T.GCTK,T.BB,T.DRN,T.HUBS,T.DNA,T.IQ,T.PLTR,T.FSLR,T.QLD,T.PAAS,T.PBLA,T.BIOL,T.HSBC,T.PEP,T.AUGX,T.MFH,T.CPE,T.DOG,T.HUDI,T.COSM,T.WDAY,T.XELA,T.DB,T.INPX,T.SNN,T.PDD,T.FXI,T.SSRM,T.TSLQ,T.HMPT,T.BYND,T.QID,T.GRIL,T.GSK,T.BNKD,T.HSTO,T.CTLT,T.ZION,T.HCDI,T.WETG,T.VXX,T.SAVA,T.SVXY,T.AKAN,T.BFLY,T.CARR,T.CLSK,T.IBM,T.DWAC,T.NKE,T.NVDS,T.INCR,T.MSTR,T.T,T.TSM,T.CSCO,T.DADA,T.SQ,T.ZSL,T.SARK,T.IVV,T.SDS,T.SPLG,T.UDOW,T.HMY,T.DXF,T.PFE,T.OMH,T.MHNC,T.SIGA,T.ATXG,T.NOGN,T.LYFT,T.RIOT,T.BMY,T.FAS,T.TECL,T.SPG,T.NDAQ,T.GNS,T.SPXL,T.WAL,T.SVIX,T.UNG,T.NOK,T.SAI,T.ARKK,T.AKLI,T.RKLB,T.AGFY,T.DDOG,T.RBLX,T.NBTX,T.MCRB,T.SNAP,T.TIO,T.HGEN,T.TAL,T.WOOF,T.DIA,T.BTBT,T.MGAM,T.UPWK,T.TWOU,T.AMC,T.YANG,T.WISA,T.ABBV,T.TCOM,T.SMTC,T.MAXN,T.UPST,T.CMND,T.ARR,T.ROKU,T.FISV,T.TSLS,T.MU,T.RELI,T.AI,T.AZN,T.NU,T.ZVZZT,T.TLRY,T.BURU,T.XLP,T.EEFT,T.CROX,T.ADXN,T.PBR,T.CXAIW,T.BITI,T.APE,T.ABB,T.FNGD,T.XLV,T.SENS,T.APLD,T.HOLO,T.TSP,T.HUIZ,T.ATEST,T.NVS,T.TLTW,T.MOS,T.DPST,T.EJH,T.BPT,T.SHEL,T.SPRO,T.SVOL,T.COIN,T.NCMI,T.TMC,T.MGIH,T.DHY,T.FOXA,T.TRUP,T.WPM,T.NVAX,T.SQM,T.KWEB,T.ZH,T.EVA,T.SNGX,T.LICY,T.HIMX,T.SPYG,T.RWM,T.VXRT,T.CTVA,T.MS,T.SPCE,T.ADBE,T.ING,T.FLNC,T.TDOC,T.NNOX,T.ENPH,T.NCLH,T.MCD,T.GDXU";
    public static final double HIT = 0.5d;
    public static final int CLOSE_PRICE = 7;
    public static final int DELAY_MINUTE = 15;
    public static final long STANDARD_TIME = 1684848600000L;
    public static final long LISTEN_END_TIME = STANDARD_TIME + 10000L;
    private static LocalDateTime dayLight_1 = LocalDateTime.of(2023, 3, 12, 0, 0, 0);
    private static LocalDateTime dayLight_2 = LocalDateTime.of(2023, 11, 6, 0, 0, 0);

    private boolean subscribed = false;
    public static Map<String, Double> stockToLastDn = Maps.newHashMap();
    public static Map<String, Double> stockToM19CloseSum = Maps.newHashMap();
    public static Map<String, Set<Double>> stockToM19Close = Maps.newHashMap();
    public static Map<String, OverBollingerDN2023OpenFirst.StockRatio> originRatioMap;
    private BlockingQueue<String> bq = new LinkedBlockingQueue<>(1000);
    private Executor executor;
    private long preTradeTime;
    private Map<String, Set<Double>> stockPreTradeMap = Maps.newHashMap();
    private boolean listenEnd = false;
    private NodeList list = new NodeList(10);

    private static Set<String> stockSet;
    private static Set<String> unsubcribeStockSet = Sets.newHashSet();
    private static Map<String, String> fileMap;
    private static AsyncEventBus eventBus;
    private FutuListener futuListener;

    Session userSession = null;
    private MessageHandler messageHandler;

    public WebsocketClientEndpoint(URI endpointURI) {
        try {
            init();
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.connectToServer(this, endpointURI);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void init() {
        try {
            String mergePath = Constants.HIS_BASE_PATH + "merge/";
            fileMap = BaseUtils.getFileMap(mergePath);
            originRatioMap = OverBollingerDN2023OpenFirst.computeHistoricalOverBollingerRatio(2023);
            stockSet = buildStockSet(fileMap);
            //            stockSet.clear();
            //            stockSet.add("AAPL");

            initPreTradeTime();
            buildExecutor();
            loadLastDn();
            loadM20();

            eventBus = asyncEventBus();
            TradeListener tradeListener = new TradeListener();
            tradeListener.setClient(this);
            tradeListener.setList(list);
            eventBus.register(tradeListener);

            futuListener = new FutuListener();
            futuListener.setList(list);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void initPreTradeTime() {
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(dayLight_1) && now.isBefore(dayLight_2)) {
            preTradeTime = now.withHour(21).withMinute(28 + DELAY_MINUTE).withSecond(0).toInstant(ZoneOffset.of("+8")).toEpochMilli();
        } else {
            preTradeTime = now.withHour(22).withMinute(28 + DELAY_MINUTE).withSecond(0).toInstant(ZoneOffset.of("+8")).toEpochMilli();
        }
    }

    private void buildExecutor() {
        int threadCount = 100;
        int corePoolSize = threadCount;
        int maximumPoolSize = corePoolSize;
        long keepAliveTime = 60L;
        LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();
        executor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.SECONDS, workQueue);
    }

    private Set<String> buildStockSet(Map<String, String> fileMap) throws Exception {
        Set<String> set = Sets.newHashSet();
        for (String stock : fileMap.keySet()) {
            String filePath = fileMap.get(stock);
            StockKLine first = BaseUtils.getLatestKLine(filePath);
            if (first.getClose() > CLOSE_PRICE) {
                set.add(stock);
            }
        }
        System.out.println("stock set1 size: " + set.size());

        for (String stock : originRatioMap.keySet()) {
            OverBollingerDN2023OpenFirst.StockRatio stockRatio = originRatioMap.get(stock);
            Map<Integer, OverBollingerDN2023OpenFirst.RatioBean> ratioMap = stockRatio.getRatioMap();
            boolean hitFailed = true;
            for (OverBollingerDN2023OpenFirst.RatioBean ratio : ratioMap.values()) {
                double ratioVal = ratio.getRatio();
                if (ratioVal > HIT) {
                    hitFailed = false;
                }
            }
            if (hitFailed) {
                set.remove(stock);
            }
        }
        System.out.println("stock set2 size: " + set.size());

        for (String stock : set) {
            stockPreTradeMap.put(stock, Sets.newHashSet());
        }
        return set;
    }

    private static void loadM20() throws Exception {
        int beforeYear = 2023, afterYear = 2021;
        Map<String, Map<String, StockKLine>> dateToStockLineMap = Maps.newHashMap();
        for (String stock : stockSet) {
            if (StringUtils.isNotBlank(TEST_STOCK) && !stock.equals(TEST_STOCK)) {
                continue;
            }
            String filePath = fileMap.get(stock);
            List<StockKLine> kLines = BaseUtils.loadDataToKline(filePath, beforeYear, afterYear);

            for (StockKLine kLine : kLines) {
                String date = kLine.getDate();
                if (!dateToStockLineMap.containsKey(date)) {
                    dateToStockLineMap.put(date, Maps.newHashMap());
                }
                dateToStockLineMap.get(date).put(stock, kLine);
            }
        }

        List<StockKLine> kLines = BaseUtils.loadDataToKline(Constants.HIS_BASE_PATH + "merge/AAPL", beforeYear, afterYear);

        List<String> _19day = Lists.newArrayList();
        for (int i = 0; i < 19; i++) {
            _19day.add(kLines.get(i).getDate());
        }

        for (String stock : stockSet) {
            if (StringUtils.isNotBlank(TEST_STOCK) && !stock.equals(TEST_STOCK)) {
                continue;
            }
            BigDecimal m20close = BigDecimal.ZERO;
            Set<Double> _19Close = Sets.newHashSet();
            boolean failed = false;
            for (String day : _19day) {
                StockKLine temp = dateToStockLineMap.get(day).get(stock);
                if (temp == null) {
                    failed = true;
                    break;
                }
                double close = temp.getClose();
                m20close = m20close.add(BigDecimal.valueOf(close));
                _19Close.add(close);
            }
            if (failed) {
                continue;
            }

            stockToM19CloseSum.put(stock, m20close.doubleValue());
            stockToM19Close.put(stock, _19Close);
        }
    }

    private static void loadLastDn() throws Exception {
        for (String stock : stockSet) {
            if (StringUtils.isNotBlank(TEST_STOCK) && !stock.equals(TEST_STOCK)) {
                continue;
            }
            List<BOLL> bolls = BaseUtils.readBollFile(Constants.HIS_BASE_PATH + "mergeBoll/" + stock, 2023, 2022);
            if (CollectionUtils.isEmpty(bolls)) {
                continue;
            }
            BOLL boll = bolls.get(0);
            double dn = boll.getDn();
            stockToLastDn.put(stock, dn);
        }
    }

    public static AsyncEventBus asyncEventBus() {
        int corePoolSize = Runtime.getRuntime().availableProcessors();
        int maxPoolSize = 100;
        int keepAliveTime = 60 * 1000;

        BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(1000);
        RejectedExecutionHandler handler = new ThreadPoolExecutor.AbortPolicy();
        ThreadFactory factory = new ThreadFactory() {
            private final AtomicInteger integer = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, "TheadPool-Thread-" + integer.getAndIncrement());
            }
        };

        return new AsyncEventBus(new ThreadPoolExecutor(corePoolSize, maxPoolSize, keepAliveTime, TimeUnit.SECONDS, workQueue, factory, handler));
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
        this.userSession = null;
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
                bq.offer(message);
                //                System.out.println(message);
            } else {
                List<Map> maps = JSON.parseArray(message, Map.class);
                Map map = maps.get(0);
                String status = MapUtils.getString(map, "status");
                String msg = MapUtils.getString(map, "message");

                if ("connected".equals(status) && "Connected Successfully".equals(msg)) {
                    System.out.println(status);
                    this.sendMessage("{\"action\":\"auth\",\"params\":\"Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY\"}");
                } else if ("auth_success".equals(status) && "authenticated".equals(msg)) {
                    System.out.println(status);
                    //                    this.sendMessage("{\"action\":\"subscribe\", \"params\":\"" + TEST_SUBSCRIBE_STOCK + "\"}");
                    subscribed = true;
                    while (true) {
                        LocalDateTime now = LocalDateTime.now();
                        long nowTime = now.toInstant(ZoneOffset.of("+8")).toEpochMilli();
                        if (nowTime < preTradeTime) {
                            System.out.println("wait pre trade time. now is " + now.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                            Thread.sleep(5000);
                        } else {
                            System.out.println("begin subcribe at " + now.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                            break;
                        }
                    }
                    executor.execute(() -> {
                        //                        Set<String> stocks = Sets.newHashSet("CXAI", "SLV", "DRMA", "FMS", "SOXS", "GFAI", "APPH", "TNA", "IWM", "TZA", "TWM", "SRTY", "NFLX", "JEPI", "META", "AUD", "IONQ", "TIVC", "TSLA", "CYTO", "FFIE", "GPRO", "TMF", "MO", "BBIG", "SOXL", "BBLN", "MINM", "MSFT", "UVXY", "GOOG", "MVST", "NVDA", "TQQQ", "GETR", "ATNF", "IBRX", "AMZN", "PLX", "UPRO", "UCO", "PACW", "SPY", "MARA", "AGBA", "ZFOX", "INTC", "DIS", "PYPL", "CDTX", "INBS", "LICN", "SPXU", "SRTS", "SBFM", "TSLL", "NRGU", "SOFI", "SQQQ", "AIG", "U", "O", "EGIO", "MTC", "IEF", "TLT", "CWEB", "TCBP", "SVFD", "GDC", "TRKA", "AAPL", "SSO", "IDEX", "VRM", "SNDL", "NTEST", "GMDA", "ABNB", "TMV", "SDOW", "VCIG", "XLI", "CRKN", "GDXD", "VSSYW", "KNTE", "ASML", "NKLA", "AMD", "OPK", "UVIX", "SBSW", "JD", "LABU", "BABA", "PTGX", "CS", "BITO", "NIO", "TCRX", "XPEV", "SFWL", "OPEN", "BNTX", "GOOGL", "INDA", "ZURA", "LILM", "PBTS", "ADMP", "Z", "RYAAY", "WMT", "CISO", "USO", "CCL", "BOIL", "TUP", "AFRM", "SLGG", "OPFI", "BNKU", "F", "BE", "SURG", "MWG", "LCID", "RIVN", "NVO", "SOS", "CNEY", "SNTG", "POLA", "IZM", "OXY", "QBTS", "GFI", "GOEV", "EPAM", "HOUR", "BLBD", "GOLD", "JAGX", "GLD", "TWLO", "EURN", "JOBY", "CVX", "VIXY", "ALLR", "SMX", "MRNA", "HYZN", "BFRG", "ZIM", "NOBL", "ORGO", "VATE", "IAU", "SCHD", "AGQ", "VOO", "XLF", "SOUN", "GETY", "UAVS", "CRSP", "KO", "PTEST", "CVNA", "CI", "KOLD", "WFC", "BLUE", "CJJD", "GDX", "LABD", "BLNK", "BAC", "MULN", "LUNR", "IMGN", "IONM", "FUTU", "SHFS", "SONO", "STSS", "CEI", "CMCSA", "CYTK", "HLN", "IREN", "SBUX", "ZOM", "TCJH", "C", "QQQ", "TOP", "UFAB", "RACE", "HKD", "AMPE", "QQQM", "DDL", "YINN", "ASTI", "UBS", "TCMD", "FNGU", "SPXS", "LAC", "PLUG", "TRVN", "QCOM", "NVTA", "JEPQ", "ICUCW", "BULZ", "BRDS", "GRVY", "CLRO", "SHOP", "MOBQ", "LI", "VALE", "GCTK", "BB", "DRN", "HUBS", "DNA", "IQ", "PLTR", "FSLR", "QLD", "PAAS", "PBLA", "BIOL", "HSBC", "PEP", "AUGX", "MFH", "CPE", "DOG", "HUDI", "COSM", "WDAY", "XELA", "DB", "INPX", "SNN", "PDD", "FXI", "SSRM", "TSLQ", "HMPT", "BYND", "QID", "GRIL", "GSK", "BNKD", "HSTO", "CTLT", "ZION", "HCDI", "WETG", "VXX", "SAVA", "SVXY", "AKAN", "BFLY", "CARR", "CLSK", "IBM", "DWAC", "NKE", "NVDS", "INCR", "MSTR", "T", "TSM", "CSCO", "DADA", "SQ", "ZSL", "SARK", "IVV", "SDS", "SPLG", "UDOW", "HMY", "DXF", "PFE", "OMH", "MHNC", "SIGA", "ATXG", "NOGN", "LYFT", "RIOT", "BMY", "FAS", "TECL", "SPG", "NDAQ", "GNS", "SPXL", "WAL", "SVIX", "UNG", "NOK", "SAI", "ARKK", "AKLI", "RKLB", "AGFY", "DDOG", "RBLX", "NBTX", "MCRB", "SNAP", "TIO", "HGEN", "TAL", "WOOF", "DIA", "BTBT", "MGAM", "UPWK", "TWOU", "AMC", "YANG", "WISA", "ABBV", "TCOM", "SMTC", "MAXN", "UPST", "CMND", "ARR", "ROKU", "FISV", "TSLS", "MU", "RELI", "AI", "AZN", "NU", "ZVZZT", "TLRY", "BURU", "XLP", "EEFT", "CROX", "ADXN", "PBR", "CXAIW", "BITI", "APE", "ABB", "FNGD", "XLV", "SENS", "APLD", "HOLO", "TSP", "HUIZ", "ATEST", "NVS", "TLTW", "MOS", "DPST", "EJH", "BPT", "SHEL", "SPRO", "SVOL", "COIN", "NCMI", "TMC", "MGIH", "DHY", "FOXA", "TRUP", "WPM", "NVAX", "SQM", "KWEB", "ZH", "EVA", "SNGX", "LICY", "HIMX", "SPYG", "RWM", "VXRT", "CTVA", "MS", "SPCE", "ADBE", "ING", "FLNC", "TDOC", "NNOX", "ENPH", "NCLH", "MCD", "GDXU");
                        //                        for (String stock : stocks) {
                        for (String stock : stockSet) {
                            sendMessage("{\"action\":\"subscribe\", \"params\":\"T." + stock + "\"}");
                        }
                    });
                }
            }
        } catch (Exception e) {
            System.out.println("onMessage " + e.getMessage());
        }
    }

    @OnMessage
    public void onMessage(ByteBuffer bytes) {
        System.out.println("Handle byte buffer");
    }

    /**
     * register message handler
     *
     * @param msgHandler
     */
    public void addMessageHandler(MessageHandler msgHandler) {
        this.messageHandler = msgHandler;
    }

    /**
     * Send a message.
     *
     * @param message
     */
    public void sendMessage(String message) {
        this.userSession.getAsyncRemote().sendText(message);
    }

    public void sendToListener() {
        try {
            while (true) {
                String msg = bq.take();
                if (listenEnd) {
                    return;
                }

                //                System.out.println(msg);
                List<Map> maps = JSON.parseArray(msg, Map.class);
                Map<String, Double> stockToPrice = Maps.newHashMap();
                for (Map map : maps) {
                    String stock = MapUtils.getString(map, "sym", "");

                    if (unsubcribeStockSet.contains(stock) || StringUtils.isBlank(stock)) {
                        //                        System.out.println(msg);
                        continue;
                    }
                    Double price = MapUtils.getDouble(map, "p");
                    Long time = MapUtils.getLong(map, "t");
                    if (time < STANDARD_TIME) {
                        //                        System.out.println("time is early");
                        continue;
                    }
                    if (time > LISTEN_END_TIME) {
                        unsubscribeAll();
                        listenEnd = true;
                        futuListener.beginTrade();
                        return;
                    }
                    // 当前价大于前一天的下轨则直接过滤
                    Double lastDn = stockToLastDn.get(stock);
                    if (lastDn == null || price > lastDn) {
                        unsubscribe(stock);
                        continue;
                    }
                    stockToPrice.put(stock, price);
                }
                for (Map.Entry<String, Double> entry : stockToPrice.entrySet()) {
                    String stock = entry.getKey();
                    if (unsubscribe(stock)) {
                        eventBus.post(entry);
                    }
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public boolean unsubscribe(String stock) {
        if (unsubcribeStockSet.contains(stock)) {
            return false;
        }
        executor.execute(() -> sendMessage("{\"action\":\"unsubscribe\", \"params\":\"T." + stock + "\"}"));
        unsubcribeStockSet.add(stock);
        if (unsubcribeStockSet.size() % 100 == 0) {
            System.out.println("unsubcribeStockSet size: " + unsubcribeStockSet.size() + " " + System.currentTimeMillis());
        }
        return true;
    }

    public void unsubscribeAll() {
        for (String stock : stockSet) {
            unsubscribe(stock);
        }
        System.out.println("=========== subscribe end ===========");
    }

    public void analysisPreTrade(String stock, double price) {
        stockPreTradeMap.get(stock).add(price);
    }

    public static void main(String[] args) throws InterruptedException {
        WebsocketClientEndpoint client = new WebsocketClientEndpoint(URI.create("wss://delayed.polygon.io/stocks"));
        client.sendToListener();

        while (true) {
            Thread.sleep(1000);
        }
    }
}