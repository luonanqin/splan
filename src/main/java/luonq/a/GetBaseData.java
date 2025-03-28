package luonq.a;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.HttpStatus;
import org.apache.commons.httpclient.MultiThreadedHttpConnectionManager;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.time.DateFormatUtils;
import org.apache.http.client.utils.DateUtils;
import org.slf4j.LoggerFactory;
import util.BaseUtils;
import util.Stock;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

import static util.Constants.HS_BASE_PATH;
import static util.Constants.SS_BASE_PATH;

/**
 * 历史每日成交数据
 * 2025-03-22:网易api不可用，切换到百度api
 * https://finance.pae.baidu.com/vapi/v1/getquotation?srcid=5353&pointType=string&group=quotation_kline_ab&market_type=ab&newFormat=1&is_kc=0&ktype=day&finClientType=pc&end_time=2025-03-21&count=107&query=600000&code=600000
 * Created by Luonanqin on 1/14/19.
 */
public class GetBaseData {
    private static HttpClient client = new HttpClient(new MultiThreadedHttpConnectionManager());

    private static String lastDay = "19960101", today = getToday();
    private static String URL_PREFIX = "https://finance.pae.baidu.com/vapi/v1/getquotation?srcid=5353&pointType=string&group=quotation_kline_ab&market_type=ab&newFormat=1&is_kc=0&ktype=day&finClientType=pc";

    public static void main(String[] args) throws Exception {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.commons").setLevel(Level.ERROR);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("httpclient.wire").setLevel(Level.ERROR);

        //		today = "20190121";
        //		getSSData();
        //		getHSData();
        //		lastDay = getLastDay();
        lastDay = "2025-03-27";
        today = "2025-03-28";
        //        getOnedayIncrementalData(HS_BASE_PATH, Lists.newArrayList("002276", "002352"));
        getOnedayIncrementalData(HS_BASE_PATH, Stock.getHsList());
        getOnedayIncrementalData(SS_BASE_PATH, Stock.getSsList());
        //                getIncrementalData(SS_BASE_PATH, Lists.newArrayList("000001"), 2);
        //        getIncrementalData(SS_BASE_PATH, Stock.getSsList(), 2);
        //        getIncrementalData(HS_BASE_PATH, Stock.getHsList(), 2);
    }

    private static void getOnedayIncrementalData(String rootPath, List<String> codeList) throws Exception {
        getIncrementalData(rootPath, codeList, 1);
    }

    private static void getIncrementalData(String rootPath, List<String> codeList, int count) throws Exception {
        for (String code : codeList) {
            String path = rootPath + code + ".csv";
            String uri = URL_PREFIX + "&query=" + code + "&code=" + code + "&end_time=" + today + "&count=" + count;

            List<String> lines = BaseUtils.readFile(path);
            if (lines.isEmpty()) {
                System.out.println(code + " is empty");
                continue;
            }
            String last = lines.get(lines.size() - 1);
            if (last.contains(lastDay)) {
                continue;
            }

            Thread.sleep(3000);
            writeData(code, path, uri);
        }
    }

    private static void writeData(String code, String path, String uri) throws Exception {
        GetMethod getMethod = new GetMethod(uri);
        List<String> data;
        int i = client.executeMethod(getMethod);
        if (i == HttpStatus.SC_OK) {
            data = getData(getMethod);

            if (CollectionUtils.isEmpty(data)) {
                System.out.println(code + " has finish!");
                return;
            }

            FileWriter fileWriter = new FileWriter(path, true);
            for (String line : data) {
                fileWriter.write(line);
                fileWriter.write("\n");
                fileWriter.flush();
            }
            fileWriter.close();

            System.out.println(code + " success");
        } else {
            System.out.println(code + " failed");
        }
    }

    private static List<String> getData(GetMethod getMethod) throws Exception {
        InputStream stream = getMethod.getResponseBodyAsStream();
        Map map = JSON.parseObject(stream, Map.class);
        Map result = (Map) map.get("Result");
        Map newMarketData = (Map) result.get("newMarketData");
        String marketData = (String) newMarketData.get("marketData");
        String[] split = marketData.split(";");
        getMethod.releaseConnection();
        List<String> data = Lists.newArrayList();
        Arrays.stream(split).forEach(s -> data.add(s));
        return data;
    }

    private static void getHSData(String rootPath, List<String> codeList) throws IOException {
        for (String code : codeList) {
            String path = rootPath + code + ".csv";
            File file = new File(path);
            //			if (file.exists()) {
            //				System.out.println(code + " exist");
            //				continue;
            //			}

            String uri = "http://quotes.money.163.com/service/chddata.html?code=0" + code + "&start=" + lastDay + "&close=" + today + "&fields=TCLOSE;HIGH;LOW;TOPEN;LCLOSE;CHG;PCHG;TURNOVER;VOTURNOVER;VATURNOVER;TCAP;MCAP";

            writeData(uri, new FileOutputStream(path), code);
        }
    }

    private static void getSSData(String rootPath, List<String> codeList) throws IOException {
        for (String code : codeList) {
            String path = rootPath + code + ".csv";
            File file = new File(path);
            //			if (file.exists()) {
            //				System.out.println(code + " exist");
            //				continue;
            //			}
            //			if (temp < 1002088) {
            //				continue;
            //			}

            String uri = "http://quotes.money.163.com/service/chddata.html?code=1" + code + "&start=" + lastDay + "&close=" + today + "&fields=TCLOSE;HIGH;LOW;TOPEN;LCLOSE;CHG;PCHG;TURNOVER;VOTURNOVER;VATURNOVER;TCAP;MCAP";

            writeData(uri, new FileOutputStream(path), code);
        }
    }

    public static String getLastDay() throws Exception {
        List<String> checkCodeList = Lists.newArrayList("600000", "600004", "600006", "600007", "600008", "600009", "600011");
        Map<String, Integer> dayCount = new HashMap<String, Integer>();
        for (String code : checkCodeList) {
            String lastLine = getLastLine(HS_BASE_PATH + code + ".csv");
            String[] split = lastLine.split(",");
            String daytime = split[0];
            if (!dayCount.containsKey(daytime)) {
                dayCount.put(daytime, 0);
            }
            Integer count = dayCount.get(daytime);
            count++;
            dayCount.put(daytime, count);
        }

        String lastDay = null;
        int maxCount = Integer.MIN_VALUE;
        for (String day : dayCount.keySet()) {
            if (dayCount.get(day) > maxCount) {
                maxCount = dayCount.get(day);
                lastDay = day;
            }
        }

        lastDay = lastDay.replaceAll("\\-", "");
        if (StringUtils.equals(getToday(), lastDay)) {
            return null;
        } else {
            Date date = DateUtils.parseDate(lastDay, new String[] { "yyyyMMdd" });
            Calendar instance = Calendar.getInstance();
            instance.setTime(date);
            instance.add(Calendar.DAY_OF_YEAR, 1);
            return DateFormatUtils.format(instance, "yyyyMMdd");
        }
    }

    public static String getToday() {
        String today = DateFormatUtils.format(Calendar.getInstance(), "yyyyMMdd");
        return today;
    }

    public static String getLastLine(String path) throws Exception {
        FileReader fileReader = new FileReader(new File(path));
        Scanner sc = new Scanner(fileReader);
        String line = null;
        while (sc.hasNextLine()) {
            line = sc.nextLine();
            if (!sc.hasNextLine() || StringUtils.isBlank(line)) {
                break;
            }
        }
        sc.close();
        return line;
    }

    private static void writeData(String uri, FileOutputStream out, String code) throws IOException {
        GetMethod getMethod = new GetMethod(uri);
        int i = client.executeMethod(getMethod);
        if (i == HttpStatus.SC_OK) {
            InputStream responseBodyAsStream = getMethod.getResponseBodyAsStream();

            BufferedReader br = new BufferedReader(new InputStreamReader(responseBodyAsStream, "iso-8859-1"));
            List<String> data = new ArrayList<String>();
            String s = br.readLine();
            data.add(new String(s.getBytes("iso-8859-1"), "gb18030"));
            while (true) {
                s = br.readLine();
                if (StringUtils.isBlank(s)) {
                    break;
                }
                data.add(1, new String(s.getBytes("iso-8859-1"), "gb18030"));
            }
            br.close();
            responseBodyAsStream.close();

            BufferedWriter bw = new BufferedWriter(new OutputStreamWriter(out));
            for (String line : data) {
                bw.write(line + "\n");
            }
            bw.close();
            System.out.println(code + " success");
        } else {
            System.out.println(code + " failed");
        }
        out.close();
        getMethod.releaseConnection();
    }
}
