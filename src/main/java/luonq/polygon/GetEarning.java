package luonq.polygon;

import bean.EarningDate;
import bean.FinnCalendarResp;
import bean.FinnCalendars;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;
import util.BaseUtils;
import util.Constants;

import java.io.InputStream;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

public class GetEarning {

    // https://api.polygon.io/vX/reference/financials?limit=100&apiKey=Ea9FNNIdlWnVnGcoTpZsOWuCWEB3JAqY&filing_date.gte=2024-06-12&filing_date.lt=2024-06-13
    public static void main(String[] args) throws Exception {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.http").setLevel(Level.INFO);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("httpclient.wire").setLevel(Level.INFO);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.commons").setLevel(Level.ERROR);
        Map<String/* date */, Set<String>/* stock */> map = Maps.newHashMap();

        LocalDate today = LocalDate.now();
        //        String fromDate = today.format(Constants.DB_DATE_FORMATTER);
        //        String toDate = today.plusDays(5).format(Constants.DB_DATE_FORMATTER);
        LocalDate from = today.withMonth(7).withDayOfMonth(3);
        LocalDate to = today;
        HttpClient httpClient = new HttpClient();
        while (!from.isAfter(to)) {
            String fromDate = from.format(Constants.DB_DATE_FORMATTER);
//            String toDate = to.format(Constants.DB_DATE_FORMATTER);
            String url = String.format("https://finnhub.io/api/v1/calendar/earnings?from=%s&to=%s&token=cnf0db9r01qi6fto5m10cnf0db9r01qi6fto5m1g", fromDate, fromDate);

            GetMethod get = new GetMethod(url);
            httpClient.executeMethod(get);
            InputStream content = get.getResponseBodyAsStream();
            FinnCalendarResp resp = JSON.parseObject(content, FinnCalendarResp.class);
            List<FinnCalendars> calendars = resp.getEarningsCalendar();
            if (CollectionUtils.isNotEmpty(calendars)) {
                for (FinnCalendars result : calendars) {
                    String date = result.getDate();
                    String hour = result.getHour();
                    String stock = result.getSymbol();
                    if (StringUtils.isBlank(hour)) {
                        hour = EarningDate.TAS;
                    } else if (StringUtils.equalsIgnoreCase("amc", hour)) {
                        hour = EarningDate.AFTER_MARKET_CLOSE;
                    } else if (StringUtils.equalsIgnoreCase("bmo", hour)) {
                        hour = EarningDate.BEFORE_MARKET_OPEN;
                    } else if (StringUtils.equalsIgnoreCase("dmh", hour)) {
                        hour = EarningDate.DURING_MARKET_HOUR;
                    }

                    if (!map.containsKey(date)) {
                        map.put(date, Sets.newHashSet());
                    }
                    map.get(date).add(stock + " " + hour);
                }
            }
            get.releaseConnection();

            for (String date : map.keySet()) {
                Set<String> set = map.get(date);
                List<String> results = set.stream().collect(Collectors.toList());
                BaseUtils.writeFile(Constants.HIS_BASE_PATH + "earning_finnhub/" + date, results);
            }
            System.out.println("finish " + fromDate);

            from = from.plusDays(1);
        }
    }
}
