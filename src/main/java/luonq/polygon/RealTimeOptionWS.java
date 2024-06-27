package luonq.polygon;

import bean.NodeList;
import bean.StockKLine;
import bean.StockOptionEvent;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.eventbus.AsyncEventBus;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
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
import java.net.URI;
import java.nio.ByteBuffer;
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

@ClientEndpoint
@Slf4j
public class RealTimeOptionWS {

    public static final URI uri = URI.create("wss://socket.polygon.io/stocks");
    public static final int DELAY_MINUTE = 0;
    public static final long LISTENING_TIME = 30000L; // 监听时长，毫秒
    public static final int PRICE_LIMIT = 3; // 价格限制，用于限制这个价格下的股票不参与计算
    public static LocalDateTime summerTime = BaseUtils.getSummerTime(null);
    public static LocalDateTime winterTime = BaseUtils.getWinterTime(null);
    public static boolean getRealtimeQuote = false;
    public static Set<String> unsubcribeStockSet = Sets.newHashSet();

    private boolean subscribed = false;
    private long preTradeTime;
    private long openTime;
    private long listenEndTime;
    private Date closeCheckTime;
    private boolean listenEnd = false;
    private boolean listenStopLoss = false;
    private boolean reconnect = false;
    private boolean manualClose = false;
    private Set<String> stockSet = Sets.newHashSet();
    public static Map<String, Double> realtimeQuoteMap = Maps.newHashMap();
    private BlockingQueue<String> subscribeBQ = new LinkedBlockingQueue<>(1000);
    private BlockingQueue<String> stopLossBQ = new LinkedBlockingQueue<>(1000);
    private BlockingQueue<String> realtimeQuoteBQ = new LinkedBlockingQueue<>(1000);
    private AtomicBoolean hasAuth = new AtomicBoolean(false);
    private NodeList list = new NodeList(10);

    private AsyncEventBus tradeEventBus;
    private Session userSession = null;
    private ThreadPoolExecutor executor;

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

            initEventListener();

            subcribeStock();
            sendToTradeDataListener();
            close();
            log.info("option finish");
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void initVariable() {
        stockSet = BaseUtils.getOptionStock();
        userSession = null;
        subscribed = false;
        listenStopLoss = false;
        reconnect = false;
        manualClose = false;
        getRealtimeQuote = false;
        realtimeQuoteMap = Maps.newHashMap();
        subscribeBQ = new LinkedBlockingQueue<>(1000);
        stopLossBQ = new LinkedBlockingQueue<>(1000);
        realtimeQuoteBQ = new LinkedBlockingQueue<>(1000);
        listenEnd = false;
        hasAuth = new AtomicBoolean(false);
        unsubcribeStockSet = Sets.newHashSet();
        tradeEventBus = asyncEventBus();
    }

    public void connect() {
        try {
            WebSocketContainer container = ContainerProvider.getWebSocketContainer();
            container.connectToServer(this, uri);
        } catch (Exception e) {
            throw new RuntimeException(e);
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
                log.info("auth failed");
                //                System.exit(0);
                return false;
            }
        }
    }

    private void initThreadExecutor() {
        int threadCount = 2000;
        int corePoolSize = threadCount;
        int maximumPoolSize = corePoolSize;
        long keepAliveTime = 60L;
        LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();
        executor = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.MINUTES, workQueue);
    }

    public AsyncEventBus asyncEventBus() {
        return new AsyncEventBus(executor);
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
        log.info("finish initialize many time. preTradeTime=" + preTradeTime + ", openTime=" + openTime + ", closeCheckTime=" + closeCheckTime);
    }

    public void initEventListener() {
        try {
            OptionDataListener tradeDataListener = new OptionDataListener();
            tradeDataListener.setStockToKLineMap(buildLastStockKLine());
            tradeEventBus.register(tradeDataListener);
            log.info("finish init data listener");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    @OnOpen
    public void onOpen(Session userSession) {
        log.info("opening websocket");
        this.userSession = userSession;
    }

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
    }

    @OnMessage
    public void onMessage(String message) {
        try {
            if (subscribed) {
                //                log.info(message);
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
                    log.info(status);
                    sendMessage("{\"action\":\"auth\",\"params\":\"bCOGpl8MLxOILZQN811_S7czXmckpdLG\"}");
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
            for (String stock : stockSet) {
                sendMessage("{\"action\":\"subscribe\", \"params\":\"T." + stock + "\"}");
            }
            log.info("finish subcribe real time!");
        });
    }

    @OnMessage
    public void onMessage(ByteBuffer bytes) {
        log.info("Handle byte buffer");
    }

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
                Map<String, StockOptionEvent> stockToEvent = Maps.newHashMap();
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
                    if (price < PRICE_LIMIT) {
                        unsubscribe(stock);
                        continue;
                    }

                    log.info("receive data: {}", msg);
                    stockToEvent.put(stock, new StockOptionEvent(stock, price, time));
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
            log.info("sendToTradeDataListener error. " + e.getMessage());
        }
    }

    public void beginTrade(String msg) throws Exception {
        subscribed = false;
        log.info(msg);
        unsubscribeAll();
        listenEnd = true;
    }

    public void unsubscribeAll() {
        for (String stock : stockSet) {
            unsubscribe(stock);
        }
        log.info("=========== finish unsubscribe ===========");
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

    public Map<String/* stock */, StockKLine> buildLastStockKLine() throws Exception {
        Map<String/* stock */, StockKLine> stockToKLineMap = Maps.newHashMap();

        Map<String, String> klineFileMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "2024/dailyKLine");
        Set<String> optionStock = BaseUtils.getOptionStock();
        int year = 2024;
        for (String stock : optionStock) {
            String filePath = klineFileMap.get(stock);
            if (StringUtils.isBlank(filePath)) {
                continue;
            }

            List<StockKLine> stockKLines = BaseUtils.loadDataToKline(filePath, year, year - 1);
            if (CollectionUtils.isNotEmpty(stockKLines)) {
                StockKLine stockKLine = stockKLines.get(0);
                stockToKLineMap.put(stock, stockKLine);
            }
        }

        return stockToKLineMap;
    }

    public static void main(String[] args) {
        RealTimeOptionWS realTimeOptionWS = new RealTimeOptionWS();
        realTimeOptionWS.init();
    }
}
