package luonq.polygon;

import com.alibaba.fastjson.JSON;
import com.google.common.collect.Sets;
import com.google.common.eventbus.AsyncEventBus;
import lombok.extern.slf4j.Slf4j;
import luonq.listener.OptionStockListener2;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.lang3.StringUtils;
import util.BaseUtils;

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
import java.time.LocalDateTime;
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
public class RealTimeOptionWS2 {

    public static final URI uri = URI.create("wss://socket.polygon.io/options");
    public static LocalDateTime summerTime = BaseUtils.getSummerTime(null);
    public static LocalDateTime winterTime = BaseUtils.getWinterTime(null);
    public static Set<String> unsubcribeStockSet = Sets.newHashSet();
    public static boolean getQuoteForOption = false;
    public static boolean getTradeForOption = false;

    private BlockingQueue<String> optionQuoteBQ = new LinkedBlockingQueue<>(10000);
    private BlockingQueue<String> optionTradeBQ = new LinkedBlockingQueue<>(10000);
    private boolean manualClose = false;
    private AtomicBoolean hasAuth = new AtomicBoolean(false);

    private Session userSession = null;
    private ThreadPoolExecutor executor;

    private OptionTradeExecutor2 optionTradeExecutor;
    private OptionStockListener2 optionStockListener;

    public void init() {
        try {
            initThreadExecutor();
            initVariable();
            connect();
            boolean authSuccess = waitAuth();
            if (!authSuccess) {
                return;
            }
            getQuoteForOption();
            getTradeForOption();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public void initVariable() {
        userSession = null;
        manualClose = false;
//        getQuoteForOption = false;
        getTradeForOption = false;
        hasAuth = new AtomicBoolean(false);
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

    public void setOptionTradeExecutor(OptionTradeExecutor2 optionTradeExecutor) {
        this.optionTradeExecutor = optionTradeExecutor;
    }

    public void setOptionStockListener(OptionStockListener2 optionStockListener) {
        this.optionStockListener = optionStockListener;
    }

    @OnOpen
    public void onOpen(Session userSession) {
        log.info("opening websocket");
        this.userSession = userSession;
    }

    @OnClose
    public void onClose(Session userSession, CloseReason reason) {
        log.info("closing option websocket. reason=" + reason);
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
            if (getQuoteForOption || getTradeForOption) {
                optionQuoteBQ.offer(message);
                optionTradeBQ.offer(message);
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

    @OnMessage
    public void onMessage(ByteBuffer bytes) {
        log.info("Handle byte buffer");
    }

    public void sendMessage(String message) {
        this.userSession.getAsyncRemote().sendText(message);
    }

    public void getQuoteForOption() {
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
                        if (askPrice == null || bidPrice == null) { // 非摆盘数据
                            continue;
                        }

                        int askVol = MapUtils.getInteger(map, "as", 0);
                        int bidVol = MapUtils.getInteger(map, "bs", 0);
                        Map<String, String> polygonForFutuMap = optionStockListener.getPolygonForFutuMap();
                        Map<String, Double> codeToBidMap = optionTradeExecutor.getCodeToBidMap();
                        Map<String, Double> codeToAskMap = optionTradeExecutor.getCodeToAskMap();
                        Map<String, String> allCodeToQuoteMap = optionTradeExecutor.getAllCodeToQuoteMap();
                        if (askPrice != null && bidPrice != null && askPrice > 0 && bidPrice > 0 && askVol > 5 && bidVol > 5) {
                            String futuCode = MapUtils.getString(polygonForFutuMap, code, "");
                            allCodeToQuoteMap.put(futuCode, bidPrice + "|" + askPrice);

                            if (askVol > 5 && bidVol > 5) {
                                double midPrice = BigDecimal.valueOf((bidPrice + askPrice) / 2).setScale(2, RoundingMode.UP).doubleValue();
                                if (StringUtils.isNotBlank(futuCode)) {
                                    codeToBidMap.put(futuCode, bidPrice);
                                    codeToAskMap.put(futuCode, askPrice);
                                }
                                log.info("polygon quote. code={}\tbidPrice={}\tbidVol={}\taskPrice={}\taskVol={}\tmidPrice={}", code, bidPrice, bidVol, askPrice, askVol, midPrice);
                            }
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

    public void getTradeForOption() {
        getTradeForOption = true;

        executor.execute(() -> {
            try {
                while (getTradeForOption) {
                    String msg = optionTradeBQ.poll(1, TimeUnit.SECONDS);
                    if (StringUtils.isBlank(msg)) {
                        continue;
                    }

                    List<Map> maps = JSON.parseArray(msg, Map.class);
                    for (Map map : maps) {
                        String code = MapUtils.getString(map, "sym", "");
                        if (!StringUtils.startsWithIgnoreCase(code, "O:")) {
                            continue;
                        }

                        Double price = MapUtils.getDouble(map, "p");
                        Long time = MapUtils.getLong(map, "t");
                        Integer size = MapUtils.getInteger(map, "s");
                        if (price == null || size == null || price.compareTo(0d) == 0 || size == 0) { // 非报价数据或错误数据
                            continue;
                        }

                        Map<String, String> polygonForFutuMap = optionStockListener.getPolygonForFutuMap();
                        String futuCode = MapUtils.getString(polygonForFutuMap, code, "");
                        Map<String, Double> codeToTradeMap = optionTradeExecutor.getCodeToTradeMap();
                        codeToTradeMap.put(futuCode, price);

                        log.info("polygon trade. code={}\tprice={}\ttime={}", code, price, time);
                    }
                }

            } catch (Exception e) {
                log.info("getTradeForOption error. ", e);
            }
        });
        log.info("start monitor polygon trade");
    }

    public void subscribeQuoteForOption(String optionCode) {
        sendMessage("{\"action\":\"subscribe\", \"params\":\"Q." + optionCode + "\"}");
        log.info("subscribe {} quote for option", optionCode);
    }

    public void unsubscribeQuoteForOption(String optionCode) {
        sendMessage("{\"action\":\"unsubscribe\", \"params\":\"Q." + optionCode + "\"}");
        log.info("unsubscribe {} quote for option", optionCode);
    }

    public static void main(String[] args) {
        RealTimeOptionWS2 realTimeOptionWS = new RealTimeOptionWS2();
        realTimeOptionWS.init();
    }
}
