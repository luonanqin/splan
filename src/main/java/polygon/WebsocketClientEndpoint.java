package polygon;

import bean.BOLL;
import bean.StockKLine;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import com.google.common.eventbus.AsyncEventBus;
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
import java.math.BigDecimal;
import java.net.URI;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.RejectedExecutionHandler;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@ClientEndpoint
public class WebsocketClientEndpoint {

    public static final String TEST_STOCK = "";

    private boolean subscribed = false;
    public static Map<String, Double> stockToLastDn = Maps.newHashMap();
    public static Map<String, Double> stockToM19CloseSum = Maps.newHashMap();
    public static Map<String, Set<Double>> stockToM19Close = Maps.newHashMap();
    private BlockingQueue<String> bq = new LinkedBlockingQueue<>(1000);

    private static Set<String> stockSet;
    private static Map<String, String> fileMap;
    private static AsyncEventBus eventBus;

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

    public static void init() {
        try {
            String mergePath = Constants.HIS_BASE_PATH + "merge/";
            fileMap = BaseUtils.getFileMap(mergePath);
            stockSet = fileMap.keySet();

            loadLastDn();
            loadM20();
            eventBus = asyncEventBus();
            TradeListener tradeListener = new TradeListener();
            eventBus.register(tradeListener);
        } catch (Exception e) {
            e.printStackTrace();
        }
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

        BlockingQueue<Runnable> workQueue = new ArrayBlockingQueue<>(5000);
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
        //        if (this.messageHandler != null) {
        //            this.messageHandler.handleMessage(message);
        //        }
        try {
            if (subscribed) {
                bq.offer(message);
                //                System.out.println(message);
            } else {
                List<Map> maps = JSON.parseArray(message, Map.class);
                Map map = maps.get(0);
                String status = MapUtils.getString(map, "status");
                String msg = MapUtils.getString(map, "message");

                String subscribedStock = "*";
                if (StringUtils.isNotBlank(TEST_STOCK)) {
                    subscribedStock = TEST_STOCK;
                }

                if ("connected".equals(status) && "Connected Successfully".equals(msg)) {
                    System.out.println(status);
                    this.sendMessage("{\"action\":\"auth\",\"params\":\"Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY\"}");
                } else if ("auth_success".equals(status) && "authenticated".equals(msg)) {
                    System.out.println(status);
                    this.sendMessage("{\"action\":\"subscribe\", \"params\":\"T." + subscribedStock + "\"}");
                } else if ("success".equals(status) && ("subscribed to: T." + subscribedStock).equals(msg)) {
                    System.out.println(msg);
                    subscribed = true;
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
//                System.out.println(msg);
                List<Map> maps = JSON.parseArray(msg, Map.class);
                Map<String, Double> stockToPrice = Maps.newHashMap();
                for (Map map : maps) {
                    String stock = MapUtils.getString(map, "sym");
                    Double price = MapUtils.getDouble(map, "p");
                    // 当前价大于前一天的下轨则直接过滤
                    //                Double lastDn = stockToLastDn.get(stock);
                    //                if (price > lastDn) {
                    //                    continue;
                    //                }
                    stockToPrice.put(stock, price);
                }
                for (Map.Entry<String, Double> entry : stockToPrice.entrySet()) {
                    eventBus.post(entry);
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) throws InterruptedException {
        WebsocketClientEndpoint client = new WebsocketClientEndpoint(URI.create("wss://delayed.polygon.io/stocks"));
        client.sendToListener();

        while (true) {
            System.out.println();
            Thread.sleep(1000);
        }
    }
}