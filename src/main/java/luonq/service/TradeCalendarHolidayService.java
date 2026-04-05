package luonq.service;

import bean.TradeCalendar;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import luonq.mapper.TradeCalendarMapper;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import util.Constants;

import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * 使用 Massive（原 Polygon）股票市场假期接口维护 {@code trade_calendar}。
 * 接口文档：<a href="https://massive.com/docs/rest/stocks/market-operations/market-holidays">Market Holidays</a>，
 * 路径 {@code GET /v1/marketstatus/upcoming}。官方说明该接口<strong>仅返回未来</strong>假期，当年已过去的休市日可能不会出现在响应中，
 * 若需完整历史需配合其它数据源或定期同步。
 */
@Service
@Slf4j
public class TradeCalendarHolidayService {

    @Value("${massive.api.base-url:https://api.polygon.io}")
    private String massiveBaseUrl;

    @Value("${massive.api.key:}")
    private String massiveApiKey;

    private static final int INSERT_BATCH = 500;

    @Autowired
    private TradeCalendarMapper tradeCalendarMapper;

    /**
     * 以今年 1 月 1 日～12 月 31 日为范围：周一至周五默认写入 {@code type=0}；
     * 接口中按日期去重后 {@code status} 为休市（closed/close）的日期<strong>不写入</strong>；
     * {@code early-close} 写入 {@code type=1}。同一日期若存在休市则优先视为休市（不存）。
     */
    public void syncCurrentYearUsEquityCalendar() throws Exception {
        if (StringUtils.isBlank(massiveApiKey)) {
            throw new IllegalStateException("massive.api.key is blank; set it in application.yml or env");
        }
        int year = LocalDate.now().getYear();
        String json = fetchUpcomingMarketStatusJson();
        Set<String> closedDates = new HashSet<>();
        Set<String> earlyCloseDates = new HashSet<>();
        parseHolidaySets(json, closedDates, earlyCloseDates);
        earlyCloseDates.removeAll(closedDates);

        List<TradeCalendar> rows = buildTradingDays(year, closedDates, earlyCloseDates);
        rows.sort(Comparator.comparing(TradeCalendar::getDate));

        batchInsertTradeCalendar(rows);
        log.info("trade_calendar sync year={} rows={} closedFromApi={} earlyCloseFromApi={}",
                year, rows.size(), closedDates.size(), earlyCloseDates.size());
    }

    private void batchInsertTradeCalendar(List<TradeCalendar> list) {
        if (CollectionUtils.isEmpty(list)) {
            return;
        }
        for (List<TradeCalendar> part : Lists.partition(list, INSERT_BATCH)) {
            tradeCalendarMapper.batchInsertTradeCalendar(part);
        }
    }

    private String fetchUpcomingMarketStatusJson() throws Exception {
        String base = massiveBaseUrl.endsWith("/")
                ? massiveBaseUrl.substring(0, massiveBaseUrl.length() - 1)
                : massiveBaseUrl;
        String url = base + "/v1/marketstatus/upcoming?apiKey=" + java.net.URLEncoder.encode(massiveApiKey, StandardCharsets.UTF_8.name());
        HttpGet get = new HttpGet(url);
        try (CloseableHttpClient client = HttpClients.createDefault();
             CloseableHttpResponse resp = client.execute(get)) {
            int code = resp.getStatusLine().getStatusCode();
            String body = resp.getEntity() != null ? EntityUtils.toString(resp.getEntity(), StandardCharsets.UTF_8) : "";
            if (code < 200 || code >= 300) {
                throw new IllegalStateException("massive upcoming HTTP " + code + ": " + body);
            }
            return body;
        }
    }

    /**
     * 按日期去重：同一自然日多交易所重复记录只保留一份语义（休市优先于提前闭市，见调用方 removeAll）。
     */
    static void parseHolidaySets(String jsonBody, Set<String> closedDates, Set<String> earlyCloseDates) {
        if (StringUtils.isBlank(jsonBody)) {
            return;
        }
        JSONArray arr = JSON.parseArray(jsonBody);
        if (arr == null) {
            JSONObject root = JSON.parseObject(jsonBody);
            if (root != null && root.containsKey("results")) {
                arr = root.getJSONArray("results");
            }
        }
        if (arr == null) {
            return;
        }
        for (int i = 0; i < arr.size(); i++) {
            JSONObject o = arr.getJSONObject(i);
            if (o == null) {
                continue;
            }
            String date = o.getString("date");
            String status = o.getString("status");
            if (StringUtils.isAnyBlank(date, status)) {
                continue;
            }
            if (isClosedStatus(status)) {
                closedDates.add(date);
            } else if (isEarlyCloseStatus(status)) {
                earlyCloseDates.add(date);
            }
        }
    }

    private static boolean isClosedStatus(String status) {
        String n = status.toLowerCase(Locale.ROOT).trim();
        return "closed".equals(n) || "close".equals(n);
    }

    private static boolean isEarlyCloseStatus(String status) {
        String n = status.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "").trim();
        return "earlyclose".equals(n);
    }

    static List<TradeCalendar> buildTradingDays(int year, Set<String> closedDates, Set<String> earlyCloseDates) {
        LocalDate start = LocalDate.of(year, 1, 1);
        LocalDate end = LocalDate.of(year, 12, 31);
        List<TradeCalendar> out = new ArrayList<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            DayOfWeek dow = d.getDayOfWeek();
            if (dow == DayOfWeek.SATURDAY || dow == DayOfWeek.SUNDAY) {
                continue;
            }
            String ds = d.format(Constants.DB_DATE_FORMATTER);
            if (closedDates.contains(ds)) {
                continue;
            }
            TradeCalendar tc = new TradeCalendar();
            tc.setDate(ds);
            tc.setType(earlyCloseDates.contains(ds) ? 1 : 0);
            out.add(tc);
        }
        return out;
    }
}
