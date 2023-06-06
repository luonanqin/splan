package luonq.polygon;

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
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import util.BaseUtils;
import util.Constants;

import java.math.BigDecimal;
import java.net.URI;
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

/**
 * Created by Luonanqin on 2023/5/9.
 */
public class TradeWSClient extends WebSocketClient {

    public static final String TEST_STOCK = "";

    private boolean subscribed = false;
    public static Map<String, Double> stockToLastDn = Maps.newHashMap();
    public static Map<String, Double> stockToM19CloseSum = Maps.newHashMap();
    public static Map<String, Set<Double>> stockToM19Close = Maps.newHashMap();
    private BlockingQueue<String> bq = new LinkedBlockingQueue<>(1000);

    private static Set<String> stockSet;
    private static Map<String, String> fileMap;
    private static AsyncEventBus eventBus;

    public TradeWSClient(URI serverUri) {
        super(serverUri);
        init();
    }

    public static void init() {
        try {
            String mergePath = Constants.HIS_BASE_PATH + "merge/";
            fileMap = BaseUtils.getFileMap(mergePath);
            stockSet = fileMap.keySet();

            loadLastDn();
            loadM20();
            eventBus = asyncEventBus();
            TradeDataListener tradeDataListener = new TradeDataListener();
            eventBus.register(tradeDataListener);
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

    @Override
    public void onOpen(ServerHandshake serverHandshake) {

    }

    @Override
    public void onMessage(String s) {
        try {
            if (subscribed) {
                //                bq.offer(s);
                System.out.println(s);
            } else {
                List<Map> maps = JSON.parseArray(s, Map.class);
                Map map = maps.get(0);
                String status = MapUtils.getString(map, "status");
                String message = MapUtils.getString(map, "message");

                if ("connected".equals(status) && "Connected Successfully".equals(message)) {
                    System.out.println(status);
                    this.send("{\"action\":\"auth\",\"params\":\"Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY\"}");
                } else if ("auth_success".equals(status) && "authenticated".equals(message)) {
                    System.out.println(status);
                    this.send("{\"action\":\"subscribe\", \"params\":\"T.*\"}");
                } else if ("success".equals(status) && "subscribed to: T.*".equals(message)) {
                    System.out.println(message);
                    subscribed = true;
                }
            }
        } catch (Exception e) {
            System.out.println("onMessage " + e.getMessage());
        }
    }

    @Override
    public void onClose(int i, String s, boolean b) {
        System.out.println("onClose");
        System.out.println(i + " " + s + " " + b);
    }

    @Override
    public void onError(Exception e) {
        e.printStackTrace();
    }

    public void sendToListener() {
        try {
            while (true) {
                String msg = bq.take();
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
        TradeWSClient client = new TradeWSClient(URI.create("wss://socket.polygon.io/stocks"));
        client.connect();
        client.sendToListener();

        while (true) {
            System.out.println();
            Thread.sleep(1000);
        }
    }
}
