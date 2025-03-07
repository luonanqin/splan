package luonq.polygon;

import bean.Trade;
import bean.TradeResp;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.URI;
import org.apache.commons.httpclient.methods.GetMethod;
import org.slf4j.LoggerFactory;
import util.BaseUtils;
import util.Constants;

import java.io.InputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Created by Luonanqin on 2023/4/28.
 */
@Slf4j
public class ShowHistoricalTrade {

    public static boolean retry = false;
    public static int limit = 50000;
    public static String apiKey = "apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY";

    public static List<String> getData(String code, String date) throws Exception {
        String api = "https://api.polygon.io/v3/trades/";
        String timeLte = "timestamp.lte=";
        String timeGte = "timestamp.gte=";

        String yearStr = date.substring(0, 4);
        int year = Integer.parseInt(yearStr);
        LocalDateTime summerTime = BaseUtils.getSummerTime(year);
        LocalDateTime winterTime = BaseUtils.getWinterTime(year);

        HttpClient httpClient = new HttpClient();
        LocalDateTime day = LocalDate.parse(date, Constants.DB_DATE_FORMATTER).atTime(0, 0);
        List<String> result = Lists.newArrayList();

        int startHour, endHour, minute = 30, seconds = 0;
        if (day.isAfter(summerTime) && day.isBefore(winterTime)) {
            startHour = 21;
            endHour = 4;
        } else {
            startHour = 22;
            endHour = 5;
        }
        LocalDateTime gte = day.withHour(startHour).withMinute(30).withSecond(seconds);
        LocalDateTime lte = day.plusDays(1).withHour(endHour).withMinute(0).withSecond(seconds);

        long gteTimestamp = gte.toInstant(ZoneOffset.of("+8")).toEpochMilli();
        long lteTimestamp = lte.toInstant(ZoneOffset.of("+8")).toEpochMilli();

        String url = api + code + "?" + timeGte + gteTimestamp + "000000&" + timeLte + lteTimestamp + "000000&order=asc&limit=" + limit + "&" + apiKey;

        result = getTrade(url, httpClient);
        List<String> res = Lists.newArrayList();
        if (CollectionUtils.isEmpty(result)) {
            log.info(code + " is empty");
            return res;
        }

        try {
            //            Collections.sort(result, (o1, o2) -> {
            //                o1 = o1.substring(0, o1.indexOf(","));
            //                o2 = o2.substring(0, o2.indexOf(","));
            //                return (int) (Long.valueOf(o1) - Long.valueOf(o2));
            //            });
            for (String s : result) {
                String[] split = s.split(",");
                String timestamp = split[0];
                LocalDateTime dateTime = LocalDateTime.ofInstant(Instant.ofEpochMilli(Long.valueOf(timestamp) / 1000000), ZoneId.systemDefault());
                String format = dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS"));
                res.add(format + "\t" + split[1] + "\t" + split[2]);
            }
        } catch (Exception e) {
            log.error("error: " + result);
        }
        return res;
    }

    public static List<String> getTrade(String url, HttpClient httpClient) {
        GetMethod get = new GetMethod(url);

        List<String> results = Lists.newArrayList();
        String result = "";
        boolean success = true;
        try {
            while (true) {
                int code = 0;
                for (int i = 0; i < 3; i++) {
                    code = httpClient.executeMethod(get);
                    if (code == 200) {
                        break;
                    }
                }
                if (code != 200) {
                    //                    System.err.println("request failed");
                    result = "request failed";
                    success = false;
                    break;
                }
                InputStream stream = get.getResponseBodyAsStream();
                TradeResp tickerResp = JSON.parseObject(stream, TradeResp.class);
                int count = tickerResp.getCount();
                String next_url = tickerResp.getNext_url();
                List<Trade> trades = tickerResp.getResults();
                for (Trade trade : trades) {
                    long timestamp = trade.getSip_timestamp();
                    int size = trade.getSize();
                    double price = trade.getPrice();
                    results.add(timestamp + "," + price + "," + size);
                }

                if (count < limit) {
                    break;
                }

                next_url += apiKey;
                get.setURI(new URI(next_url, false));
            }
        } catch (Exception e) {
            result = "request error";
        } finally {
            get.releaseConnection();
        }
        return results;
    }

    public static void main(String[] args) throws Exception {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.commons").setLevel(Level.ERROR);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("httpclient.wire").setLevel(Level.ERROR);

        //        List<String> data = getData("O:TSLA241108C00320000", "2024-11-08");
        //        List<String> data = getData("O:TSLA250103C00400000", "2025-01-03");
        //        List<String> data = getData("O:AAPL240202C00182500", "2024-02-02");
        //    List<String> data = getData("O:COIN240823C00207500", "2024-08-23");
        //    List<String> data = getData("O:MSTR250110C00332500", "2025-01-10");
        List<String> data = getData("O:LLY250117P00730000", "2025-01-17");
        for (int i = 0; i < 20000 && i < data.size(); i++) {
            System.out.println(data.get(i));
        }
        System.out.println(data.get(data.size() - 1));
        log.info("============ end ============");
    }
}
