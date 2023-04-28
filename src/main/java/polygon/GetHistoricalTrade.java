package polygon;

import bean.StockKLine;
import bean.Trade;
import bean.TradeResp;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.methods.GetMethod;
import util.BaseUtils;
import util.Constants;

import java.io.FileWriter;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Created by Luonanqin on 2023/4/28.
 */
public class GetHistoricalTrade {

    public static void main(String[] args) throws Exception {
        String apiKey = "apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY";
        String api = "https://api.polygon.io/v3/trades/";
        String timeLte = "timestamp.lte=";
        String timeGte = "timestamp.gte=";
        // https://api.polygon.io/v3/trades/FUTU?timestamp=2023-04-27&order=desc&limit=1000&sort=timestamp&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY

        // 2023
        LocalDateTime dayLight = LocalDateTime.of(2023, 3, 12, 0, 0, 0);
        int limit = 100;
        int year = 2023;

        // 2022

        Map<String, String> hasMergeMap = BaseUtils.getFileMap(Constants.TRADE_OPEN_PATH + year);
        LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();
        Executor cachedThread = new ThreadPoolExecutor(20, 20, 10, TimeUnit.SECONDS, workQueue);

        Map<String, String> stockMap = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "2023daily");
        for (String stock : stockMap.keySet()) {
            if (!stock.equals("FUTU")) {
                //                continue;
            }
            if (hasMergeMap.containsKey(stock)) {
                continue;
            }
            String stockFile = stockMap.get(stock);
            List<StockKLine> stockKLines = BaseUtils.loadDataToKline(stockFile, 2023);

            List<String> result = Collections.synchronizedList(Lists.newArrayList());
            Lock lock = new ReentrantLock();
            Condition cond = lock.newCondition();
            CountDownLatch cdl = new CountDownLatch(stockKLines.size());
            long begin = System.currentTimeMillis();
            AtomicInteger size = new AtomicInteger(10);
            for (StockKLine stockKLine : stockKLines) {
                lock.lock();
                try {
                    while (true) {
                        if (size.get() < 0) {
                            cond.await();
                            continue;
                        }
                        //                        System.out.println("wake up");
                        break;
                    }
                    size.decrementAndGet();
                    cachedThread.execute(() -> {
                        String date = stockKLine.getDate();
                        LocalDateTime day = LocalDate.parse(date, Constants.FORMATTER).atTime(0, 0);

                        int hour, minute = 30, seconds = 0;
                        if (day.isAfter(dayLight)) {
                            hour = 21;
                        } else {
                            hour = 22;
                        }
                        LocalDateTime gte = day.withHour(hour).withMinute(minute).withSecond(seconds);
                        LocalDateTime lte = day.withHour(hour).withMinute(minute + 1).withSecond(seconds);

                        long gteTimestamp = gte.toInstant(ZoneOffset.of("+8")).toEpochMilli();
                        long lteTimestamp = lte.toInstant(ZoneOffset.of("+8")).toEpochMilli();

                        String url = api + stock + "?" + timeGte + gteTimestamp + "000000&" + timeLte + lteTimestamp + "000000&limit=" + limit + "&" + apiKey;
                        HttpClient httpclient = new HttpClient();
                        GetMethod get = new GetMethod(url);

                        int totalVolumn = 0;
                        double totalAmount = 0;
                        try {
                            while (true) {
                                int code = httpclient.executeMethod(get);
                                if (code != 200) {
                                    System.err.println("request failed");
                                    result.add("request failed. date=" + date);
                                    break;
                                }
                                InputStream stream = get.getResponseBodyAsStream();
                                TradeResp tickerResp = JSON.parseObject(stream, TradeResp.class);
                                int count = tickerResp.getCount();
                                String next_url = tickerResp.getNext_url();
                                List<Trade> results = tickerResp.getResults();
                                for (Trade trade : results) {
                                    totalVolumn += trade.getSize();
                                    totalAmount += trade.getPrice() * trade.getSize();
                                }

                                if (count < limit) {
                                    break;
                                }

                                next_url += apiKey;
                                get.setURI(new URI(next_url, false));
                            }
                            double avgPrice = totalAmount / totalVolumn;
                            result.add(String.format("%s,%d,%.2f", date, totalVolumn, avgPrice));
                            //                            System.out.println("finish stock: " + stock + " date: " + date);
                            lock.lock();
                            size.incrementAndGet();
                            cond.signalAll();
                        } catch (Exception e) {
                            result.add("request error. date=" + date);
                        } finally {
                            cdl.countDown();
                            lock.unlock();
                        }
                    });
                } finally {
                    lock.unlock();
                }
            }

            cdl.await();
            Collections.sort(result, (o1, o2) -> {
                o1 = o1.substring(0, o1.indexOf(","));
                o2 = o2.substring(0, o2.indexOf(","));
                return BaseUtils.dateToInt(o2) - BaseUtils.dateToInt(o1);
            });
            FileWriter fw = new FileWriter(Constants.TRADE_OPEN_PATH + year + "/" + stock);
            for (String str : result) {
                fw.write(str + "\n");
            }
            fw.close();
            System.out.println("stock: " + stock + ", cost: " + ((System.currentTimeMillis() - begin) / 1000) + "s");
        }
    }
}
