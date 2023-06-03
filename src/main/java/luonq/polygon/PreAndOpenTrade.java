package luonq.polygon;

import bean.Trade;
import bean.TradeResp;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import util.BaseUtils;
import util.Constants;

import java.io.IOException;
import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

/**
 * Created by Luonanqin on 2023/5/14.
 */
public class PreAndOpenTrade {

    public static HttpClient httpclient = new HttpClient();

    public static void main(String[] args) throws Exception {
        String apiKey = "apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY";
        String api = "https://api.polygon.io/v3/trades/";
        String timeLte = "timestamp.lte=";
        String timeGte = "timestamp.gte=";
        String limit = "100";

        Map<String, List<String>> dateToStocksMap = Maps.newTreeMap();
        List<String> lines = BaseUtils.readFile(Constants.TEST_PATH + "overBollingOpen");
        for (String line : lines) {
            String[] split = line.split(",");
            String date = split[0].trim().substring(5);
            String stock = split[1].trim().substring(6);
            if (!dateToStocksMap.containsKey(date)) {
                dateToStocksMap.put(date, Lists.newArrayList());
            }
            dateToStocksMap.get(date).add(stock);
        }

        // 2023
        LocalDateTime dayLight1 = LocalDateTime.of(2023, 3, 12, 0, 0, 0);
        LocalDateTime dayLight2 = LocalDateTime.of(2023, 11, 6, 0, 0, 0);
        int year = 2023;

        for (String date : dateToStocksMap.keySet()) {
            List<String> stocks = dateToStocksMap.get(date);

            LocalDateTime day = LocalDate.parse(date, Constants.FORMATTER).atTime(0, 0);
            int hour, minute = 30, seconds = 0;
            if (day.isAfter(dayLight1) && day.isBefore(dayLight2)) {
                hour = 21;
            } else {
                hour = 22;
            }

            LocalDateTime open = day.withHour(hour).withMinute(minute).withSecond(seconds);
            LocalDateTime preGte = day.withHour(hour).withMinute(minute - 29).withSecond(seconds);
            LocalDateTime openLte = day.withHour(hour).withMinute(minute + 1).withSecond(seconds);

            long openTS = open.toInstant(ZoneOffset.of("+8")).toEpochMilli();
            long preGteTS = preGte.toInstant(ZoneOffset.of("+8")).toEpochMilli();
            long openLteTS = openLte.toInstant(ZoneOffset.of("+8")).toEpochMilli();

            for (String stock : stocks) {
                String preUrl = api + stock + "?order=desc&" + timeGte + preGteTS + "000000&" + timeLte + openTS + "000000&limit=" + limit + "&" + apiKey;
                String openUrl = api + stock + "?order=asc&" + timeGte + openTS + "000000&" + timeLte + openLteTS + "000000&limit=" + limit + "&" + apiKey;

                String preTrade = getTrade(preUrl);
                String openTrade = getTrade(openUrl);

                System.out.println("date=" + date + " stock=" + stock + " preTrade=" + preTrade + " openTrade=" + openTrade);
            }
        }
    }

    private static String getTrade(String preUrl) throws IOException {
        GetMethod get = new GetMethod(preUrl);
        int code = 0;
        for (int i = 0; i < 3; i++) {
            code = httpclient.executeMethod(get);
            if (code == 200) {
                break;
            }
        }
        if (code != 200) {
            return "request error";
        }

        InputStream stream = get.getResponseBodyAsStream();
        TradeResp tickerResp = JSON.parseObject(stream, TradeResp.class);
        List<Trade> results = tickerResp.getResults();
        if (CollectionUtils.isNotEmpty(results)) {
            Trade trade = results.get(0);
            return String.valueOf(trade.getPrice());
        } else {
            return "no data";
        }
    }
}
