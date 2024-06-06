package luonq.ivolatility;

import bean.StockKLine;
import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.collections4.MapUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.LoggerFactory;
import util.BaseUtils;
import util.Constants;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class GetImpliedVolatility {
    public static BlockingQueue<HttpClient> queue;
    public static ThreadPoolExecutor cachedThread;

    public static void init() throws Exception {
        int threadCount = 1;
        int corePoolSize = threadCount;
        int maximumPoolSize = corePoolSize;
        long keepAliveTime = 60L;
        LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();
        cachedThread = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.SECONDS, workQueue);
        queue = new LinkedBlockingQueue<>(threadCount);
        for (int i = 0; i < threadCount; i++) {
            queue.offer(new HttpClient());
        }
    }

    // https://restapi.ivolatility.com/equities/eod/option-series-on-date?apiKey=S3j7pBefWG0J0glb&symbol=BILI&expFrom=2022-05-20&expTo=2022-05-20&strikeFrom=22&strikeTo=22&date=2022-05-20
    public static Map<String/* optioncode */, String/* optionId */> getOptionId(List<String> optionCodeList) throws Exception {
        Map<String, String> optionIdMap = getOptionIdMap();
        Map<String, String> optionCodeToIdMap = Maps.newHashMap();
        for (String optionCode : optionCodeList) {
            if (optionIdMap.containsKey(optionCode)) {
                continue;
            }

            HttpClient httpClient = queue.take();
            int _2_index = optionCode.indexOf("2");
            String stock = optionCode.substring(2, _2_index);

            String date = optionCode.substring(_2_index, optionCode.length() - 9);
            String formatDate = LocalDate.parse("20" + date, DateTimeFormatter.ofPattern("yyyyMMdd")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            String contractType = optionCode.substring(optionCode.length() - 9, optionCode.length() - 8);

            String priceStr = optionCode.substring(optionCode.length() - 8);
            int priceInt = Integer.valueOf(priceStr);
            String strikePrice = BigDecimal.valueOf(priceInt).divide(BigDecimal.valueOf(1000)).setScale(1, RoundingMode.DOWN).toString();
            if (strikePrice.contains(".0")) {
                strikePrice = strikePrice.substring(0, strikePrice.length() - 2);
            }
            //            System.out.println(strikePrice);
            //            System.out.println(formatDate);

            String url = String.format("https://restapi.ivolatility.com/equities/eod/option-series-on-date?apiKey=S3j7pBefWG0J0glb&symbol=%s&expFrom=%s&expTo=%s&strikeFrom=%s&strikeTo=%s&callPut=%s&date=%s",
              stock, formatDate, formatDate, strikePrice, strikePrice, contractType, formatDate);
            GetMethod get = new GetMethod(url);

            //            cachedThread.execute(() -> {
            try {
                int status = httpClient.executeMethod(get);
                String content = get.getResponseBodyAsString();
                //                System.out.println(status + " " + content);
                List<Map> listMap = JSON.parseArray(content, Map.class);
                if (CollectionUtils.isEmpty(listMap)) {
                    System.out.println(optionCode + " is empth");
                    continue;
                }
                if (status == 429) {
                    System.out.println(429);
                    continue;
                    //                    System.exit(0);
                }

                Map map = listMap.get(0);
                String optionId = MapUtils.getString(map, "optionId");
                System.out.println(optionCode + "\t" + optionId);

            } catch (Exception e) {
                e.printStackTrace();
            } finally {
                get.releaseConnection();
                queue.offer(httpClient);
            }
            Thread.sleep(1000);
            //            });
        }

        return optionCodeToIdMap;
    }

    // https://restapi.ivolatility.com/equities/eod/single-stock-option-raw-iv?apiKey=S3j7pBefWG0J0glb&optionId=120387742&from=2022-05-16&to=2022-05-20
    public static void getHistoricalIV(Map<String/* optionCode */, String/* date */> optionCodeDateMap) throws Exception {
        List<StockKLine> stockKLines = BaseUtils.loadDataToKline(Constants.HIS_BASE_PATH + "merge/AAPL", 2024, 2020);
        List<String> dateList = stockKLines.stream().map(StockKLine::getFormatDate).collect(Collectors.toList());
        Map<String/* today */, String/* last5Days*/> last5DaysMap = Maps.newHashMap();
        for (int i = 0; i < dateList.size() - 6; i++) {
            last5DaysMap.put(dateList.get(i), dateList.get(i + 5));
        }
        Map<String, String> optionIdMap = getOptionIdMap();

        HttpClient httpClient = new HttpClient();
        for (String optionCode : optionCodeDateMap.keySet()) {
            String optionId = optionIdMap.get(optionCode);
            if (StringUtils.isBlank(optionId)) {
                continue;
            }
            String date = optionCodeDateMap.get(optionCode);
            String last5Day = last5DaysMap.get(date);
            String url = String.format("https://restapi.ivolatility.com/equities/eod/single-stock-option-raw-iv?apiKey=S3j7pBefWG0J0glb&optionId=%s&from=%s&to=%s",
              optionId, last5Day, date);

            GetMethod get = new GetMethod(url);
            String content = null;
            try {
                httpClient.executeMethod(get);
                content = get.getResponseBodyAsString();
                JSONObject map = JSON.parseObject(content, JSONObject.class);
                Object data = map.get("data");
                List<Map> maps = JSON.parseArray(data.toString(), Map.class);
                if (CollectionUtils.isEmpty(maps)) {
                    continue;
                }
                System.out.print(optionCode + "\t");
                for (Map m : maps) {
                    String curDate = MapUtils.getString(m, "date");
                    Double iv = MapUtils.getDouble(m, "iv");
                    if (curDate.equals(date)) {
                        break;
                    }
                    System.out.print(curDate + "\t" + iv + "\t");
                }
                System.out.println();
            } catch (Exception e) {
                System.out.println(url + "\t" + content);
                e.printStackTrace();
            } finally {
                get.releaseConnection();
            }
            Thread.sleep(1000);
        }
    }

    public static Map<String, String> getOptionCodeDateMap() {
        Map<String, String> map = Maps.newHashMap();
        map.put("O:LVS220128C00044000","2022-01-24");
        map.put("O:BA220128C00202500","2022-01-24");
        map.put("O:BA220128C00202500","2022-01-25");
        map.put("O:SNAP220204C00035000","2022-02-01");
        map.put("O:ABNB220211C00167500","2022-02-10");
        map.put("O:UPST220211C00109000","2022-02-10");
        map.put("O:RBLX220218C00069000","2022-02-14");
        map.put("O:KGC220218C00006000","2022-02-15");
        map.put("O:GOLD220218C00021000","2022-02-15");
        map.put("O:SPWR220218C00017000","2022-02-15");
        map.put("O:DKNG220218C00023000","2022-02-15");
        map.put("O:CROX220218C00105000","2022-02-15");
        map.put("O:TTD220218C00080000","2022-02-15");
        map.put("O:ROKU220218C00165000","2022-02-15");
        map.put("O:NVDA220218C00252500","2022-02-15");
        map.put("O:SPCE220218C00010500","2022-02-16");
        map.put("O:ROKU220218C00165000","2022-02-16");
        map.put("O:FUBO220218C00011000","2022-02-17");
        map.put("O:SPCE220218C00011000","2022-02-17");
        map.put("O:RKT220225C00012500","2022-02-22");
        map.put("O:NCLH220225C00021000","2022-02-22");
        map.put("O:BABA220225C00115000","2022-02-22");
        map.put("O:NCLH220225C00021000","2022-02-23");
        map.put("O:LAZR220225C00015000","2022-02-23");
        map.put("O:AMC220225C00015500","2022-02-24");
        map.put("O:MARA220225C00020000","2022-02-24");
        map.put("O:FCEL220311C00006000","2022-03-07");
        map.put("O:AG220311C00013500","2022-03-08");
        map.put("O:AG220311C00013000","2022-03-09");
        map.put("O:BLNK220311C00025500","2022-03-09");
        map.put("O:RIVN220311C00044000","2022-03-09");
        map.put("O:TSM220414C00101000","2022-04-12");
        map.put("O:SNAP220422C00033500","2022-04-20");
        map.put("O:X220429C00033000","2022-04-27");
        map.put("O:COIN220506C00126000","2022-05-05");
        map.put("O:CPNG220513C00012000","2022-05-09");
        map.put("O:NCLH220513C00018500","2022-05-09");
        map.put("O:AFRM220513C00025000","2022-05-09");
        map.put("O:CPNG220513C00010500","2022-05-10");
        map.put("O:AFRM220513C00020000","2022-05-10");
        map.put("O:DIS220513C00111000","2022-05-10");
        map.put("O:BBY220520C00080000","2022-05-19");
        map.put("O:GPS220527C00011000","2022-05-23");
        map.put("O:NVDA220527C00165000","2022-05-23");
        map.put("O:CGC220527C00005500","2022-05-24");
        map.put("O:GPS220527C00011000","2022-05-24");
        map.put("O:BABA220527C00086000","2022-05-24");
        map.put("O:NVDA220527C00167500","2022-05-24");
        map.put("O:NIO220610C00019500","2022-06-06");
        map.put("O:BB220624C00006000","2022-06-21");
        map.put("O:BB220624C00005500","2022-06-22");
        map.put("O:TSM220715C00081000","2022-07-11");
        map.put("O:TSM220715C00082000","2022-07-12");
        map.put("O:BAC220715C00030500","2022-07-14");
        map.put("O:WFC220715C00038500","2022-07-14");
        map.put("O:C220715C00045000","2022-07-14");
        map.put("O:AAL220722C00015500","2022-07-19");
        map.put("O:SNAP220722C00015000","2022-07-20");
        map.put("O:VALE220729C00013500","2022-07-25");
        map.put("O:AMZN220729C00117000","2022-07-26");
        map.put("O:PINS220729C00018500","2022-07-27");
        map.put("O:NKLA220805C00007000","2022-08-02");
        map.put("O:SAVA220805C00018000","2022-08-02");
        map.put("O:BABA220805C00089000","2022-08-02");
        map.put("O:NKLA220805C00007500","2022-08-03");
        map.put("O:NVAX220805C00060000","2022-08-03");
        map.put("O:SQ220805C00082000","2022-08-03");
        map.put("O:U220805C00044000","2022-08-04");
        map.put("O:NCLH220812C00014000","2022-08-08");
        map.put("O:PLUG220812C00027000","2022-08-08");
        map.put("O:RIVN220812C00037000","2022-08-08");
        map.put("O:COIN220812C00099000","2022-08-08");
        map.put("O:LI220812C00032000","2022-08-10");
        map.put("O:TGT220819C00180000","2022-08-16");
        map.put("O:ZM220819C00107000","2022-08-17");
        map.put("O:KSS220819C00034500","2022-08-17");
        map.put("O:PTON220826C00012000","2022-08-22");
        map.put("O:AFRM220826C00030000","2022-08-22");
        map.put("O:NVDA220826C00177500","2022-08-22");
        map.put("O:PDD220826C00049500","2022-08-24");
        map.put("O:AFRM220826C00030500","2022-08-24");
        map.put("O:CHPT220826C00016000","2022-08-25");
        map.put("O:CHPT220902C00015500","2022-08-29");
        map.put("O:GME220902C00028500","2022-09-01");
        map.put("O:MS221014C00076000","2022-10-13");
        map.put("O:NFLX221014C00215000","2022-10-13");
        map.put("O:AAL221021C00014000","2022-10-17");
        map.put("O:AA221021C00040000","2022-10-17");
        map.put("O:TSLA221021C00212500","2022-10-17");
        map.put("O:AAL221021C00014000","2022-10-18");
        map.put("O:SNAP221021C00011500","2022-10-18");
        map.put("O:FCX221021C00030000","2022-10-18");
        map.put("O:LVS221021C00037000","2022-10-18");
        map.put("O:CMA221021C00077500","2022-10-18");
        map.put("O:ABT221021C00107000","2022-10-18");
        map.put("O:BX221021C00092000","2022-10-18");
        map.put("O:TSLA221021C00232500","2022-10-18");
        map.put("O:AAL221021C00014500","2022-10-19");
        map.put("O:SNAP221021C00011000","2022-10-19");
        map.put("O:CLF221028C00016500","2022-10-24");
        map.put("O:X221028C00021000","2022-10-25");
        map.put("O:AMZN221028C00117000","2022-10-26");
        map.put("O:SOFI221104C00006000","2022-10-31");
        map.put("O:PARA221104C00019000","2022-10-31");
        map.put("O:ROKU221104C00057000","2022-10-31");
        map.put("O:GOLD221104C00016000","2022-11-01");
        map.put("O:LAZR221104C00009000","2022-11-01");
        map.put("O:HOOD221104C00012500","2022-11-01");
        map.put("O:MRO221104C00031500","2022-11-01");
        map.put("O:GOOS221104C00018000","2022-11-01");
        map.put("O:MGM221104C00037000","2022-11-01");
        map.put("O:BTU221104C00025000","2022-11-01");
        map.put("O:COIN221104C00070000","2022-11-01");
        map.put("O:MRNA221104C00157500","2022-11-01");
        map.put("O:WBD221104C00013500","2022-11-02");
        map.put("O:DKNG221104C00017000","2022-11-02");
        map.put("O:PLUG221111C00015500","2022-11-07");
        map.put("O:NIO221111C00010500","2022-11-09");
        map.put("O:RUM221111C00013000","2022-11-10");
        map.put("O:BABA221118C00074000","2022-11-14");
        map.put("O:JMIA221118C00005500","2022-11-15");
        map.put("O:AMAT221118C00112000","2022-11-15");
        map.put("O:NVDA221118C00170000","2022-11-15");
        map.put("O:TGT221118C00180000","2022-11-15");
        map.put("O:M221118C00021000","2022-11-16");
        map.put("O:JD221118C00053000","2022-11-17");
        map.put("O:XPEV221202C00007500","2022-11-28");
        map.put("O:XPEV221202C00007500","2022-11-29");
        map.put("O:LI221209C00024500","2022-12-05");
        map.put("O:LI221209C00023000","2022-12-06");
        map.put("O:LI221209C00022500","2022-12-07");
        map.put("O:LVS220128P00042000","2022-01-24");
        map.put("O:BA220128P00197500","2022-01-24");
        map.put("O:BA220128P00197500","2022-01-25");
        map.put("O:SNAP220204P00033000","2022-02-01");
        map.put("O:ABNB220211P00162500","2022-02-10");
        map.put("O:UPST220211P00107000","2022-02-10");
        map.put("O:RBLX220218P00067000","2022-02-14");
        map.put("O:KGC220218P00005000","2022-02-15");
        map.put("O:GOLD220218P00020000","2022-02-15");
        map.put("O:SPWR220218P00016000","2022-02-15");
        map.put("O:DKNG220218P00022500","2022-02-15");
        map.put("O:CROX220218P00095000","2022-02-15");
        map.put("O:TTD220218P00078000","2022-02-15");
        map.put("O:ROKU220218P00160000","2022-02-15");
        map.put("O:NVDA220218P00247500","2022-02-15");
        map.put("O:SPCE220218P00010000","2022-02-16");
        map.put("O:ROKU220218P00162500","2022-02-16");
        map.put("O:FUBO220218P00010000","2022-02-17");
        map.put("O:SPCE220218P00010000","2022-02-17");
        map.put("O:RKT220225P00011500","2022-02-22");
        map.put("O:NCLH220225P00020000","2022-02-22");
        map.put("O:BABA220225P00113000","2022-02-22");
        map.put("O:NCLH220225P00020500","2022-02-23");
        map.put("O:LAZR220225P00014000","2022-02-23");
        map.put("O:AMC220225P00014500","2022-02-24");
        map.put("O:MARA220225P00019000","2022-02-24");
        map.put("O:FCEL220311P00005000","2022-03-07");
        map.put("O:AG220311P00012500","2022-03-08");
        map.put("O:AG220311P00012000","2022-03-09");
        map.put("O:BLNK220311P00024500","2022-03-09");
        map.put("O:RIVN220311P00043000","2022-03-09");
        map.put("O:TSM220414P00099000","2022-04-12");
        map.put("O:SNAP220422P00032500","2022-04-20");
        map.put("O:X220429P00032000","2022-04-27");
        map.put("O:COIN220506P00124000","2022-05-05");
        map.put("O:CPNG220513P00011000","2022-05-09");
        map.put("O:NCLH220513P00017500","2022-05-09");
        map.put("O:AFRM220513P00024000","2022-05-09");
        map.put("O:CPNG220513P00009500","2022-05-10");
        map.put("O:AFRM220513P00018000","2022-05-10");
        map.put("O:DIS220513P00109000","2022-05-10");
        map.put("O:BBY220520P00070000","2022-05-19");
        map.put("O:GPS220527P00010000","2022-05-23");
        map.put("O:NVDA220527P00160000","2022-05-23");
        map.put("O:CGC220527P00004500","2022-05-24");
        map.put("O:GPS220527P00010000","2022-05-24");
        map.put("O:BABA220527P00084000","2022-05-24");
        map.put("O:NVDA220527P00162500","2022-05-24");
        map.put("O:NIO220610P00018500","2022-06-06");
        map.put("O:BB220624P00005000","2022-06-21");
        map.put("O:BB220624P00005000","2022-06-22");
        map.put("O:TSM220715P00079000","2022-07-11");
        map.put("O:TSM220715P00080000","2022-07-12");
        map.put("O:BAC220715P00029500","2022-07-14");
        map.put("O:WFC220715P00037500","2022-07-14");
        map.put("O:C220715P00044000","2022-07-14");
        map.put("O:AAL220722P00014500","2022-07-19");
        map.put("O:SNAP220722P00014000","2022-07-20");
        map.put("O:VALE220729P00012500","2022-07-25");
        map.put("O:AMZN220729P00115000","2022-07-26");
        map.put("O:PINS220729P00018000","2022-07-27");
        map.put("O:NKLA220805P00006000","2022-08-02");
        map.put("O:SAVA220805P00017000","2022-08-02");
        map.put("O:BABA220805P00087000","2022-08-02");
        map.put("O:NKLA220805P00006500","2022-08-03");
        map.put("O:NVAX220805P00058000","2022-08-03");
        map.put("O:SQ220805P00080000","2022-08-03");
        map.put("O:U220805P00043000","2022-08-04");
        map.put("O:NCLH220812P00013000","2022-08-08");
        map.put("O:PLUG220812P00026000","2022-08-08");
        map.put("O:RIVN220812P00036000","2022-08-08");
        map.put("O:COIN220812P00097000","2022-08-08");
        map.put("O:LI220812P00031000","2022-08-10");
        map.put("O:TGT220819P00175000","2022-08-16");
        map.put("O:ZM220819P00105000","2022-08-17");
        map.put("O:KSS220819P00033500","2022-08-17");
        map.put("O:PTON220826P00011000","2022-08-22");
        map.put("O:AFRM220826P00029000","2022-08-22");
        map.put("O:NVDA220826P00172500","2022-08-22");
        map.put("O:PDD220826P00048500","2022-08-24");
        map.put("O:AFRM220826P00029500","2022-08-24");
        map.put("O:CHPT220826P00015000","2022-08-25");
        map.put("O:CHPT220902P00014500","2022-08-29");
        map.put("O:GME220902P00027500","2022-09-01");
        map.put("O:MS221014P00074000","2022-10-13");
        map.put("O:NFLX221014P00210000","2022-10-13");
        map.put("O:AAL221021P00013000","2022-10-17");
        map.put("O:AA221021P00039000","2022-10-17");
        map.put("O:TSLA221021P00207500","2022-10-17");
        map.put("O:AAL221021P00013000","2022-10-18");
        map.put("O:SNAP221021P00010500","2022-10-18");
        map.put("O:FCX221021P00029000","2022-10-18");
        map.put("O:LVS221021P00036000","2022-10-18");
        map.put("O:CMA221021P00072500","2022-10-18");
        map.put("O:ABT221021P00105000","2022-10-18");
        map.put("O:BX221021P00090000","2022-10-18");
        map.put("O:TSLA221021P00227500","2022-10-18");
        map.put("O:AAL221021P00013500","2022-10-19");
        map.put("O:SNAP221021P00010500","2022-10-19");
        map.put("O:CLF221028P00015500","2022-10-24");
        map.put("O:X221028P00020500","2022-10-25");
        map.put("O:AMZN221028P00115000","2022-10-26");
        map.put("O:SOFI221104P00005000","2022-10-31");
        map.put("O:PARA221104P00018000","2022-10-31");
        map.put("O:ROKU221104P00055000","2022-10-31");
        map.put("O:GOLD221104P00015000","2022-11-01");
        map.put("O:LAZR221104P00008000","2022-11-01");
        map.put("O:HOOD221104P00011500","2022-11-01");
        map.put("O:MRO221104P00030500","2022-11-01");
        map.put("O:GOOS221104P00016000","2022-11-01");
        map.put("O:MGM221104P00036000","2022-11-01");
        map.put("O:BTU221104P00024000","2022-11-01");
        map.put("O:COIN221104P00068000","2022-11-01");
        map.put("O:MRNA221104P00152500","2022-11-01");
        map.put("O:WBD221104P00012500","2022-11-02");
        map.put("O:DKNG221104P00016500","2022-11-02");
        map.put("O:PLUG221111P00014500","2022-11-07");
        map.put("O:NIO221111P00009500","2022-11-09");
        map.put("O:RUM221111P00012000","2022-11-10");
        map.put("O:BABA221118P00072000","2022-11-14");
        map.put("O:JMIA221118P00004500","2022-11-15");
        map.put("O:AMAT221118P00110000","2022-11-15");
        map.put("O:NVDA221118P00165000","2022-11-15");
        map.put("O:TGT221118P00175000","2022-11-15");
        map.put("O:M221118P00020500","2022-11-16");
        map.put("O:JD221118P00051000","2022-11-17");
        map.put("O:XPEV221202P00006500","2022-11-28");
        map.put("O:XPEV221202P00007000","2022-11-29");
        map.put("O:LI221209P00023500","2022-12-05");
        map.put("O:LI221209P00022000","2022-12-06");
        map.put("O:LI221209P00021500","2022-12-07");
        return map;
    }

    public static Map<String/* optoinCode */, String/* optionId */> getOptionIdMap() throws Exception {
        List<String> lines = BaseUtils.readFile(Constants.USER_PATH + "optionData/optionId");
        Map<String, String> map = Maps.newHashMap();
        for (String line : lines) {
            String[] split = line.split("\t");
            map.put(split[0], split[1]);
        }

        return map;
    }

    public static List<String> buildTestData() {
        List<String> list = Lists.newArrayList();
        list.add("O:KGC220218C00006000");
        list.add("O:BB220624C00005500");
        list.add("O:BB220624C00006000");
        list.add("O:FUBO220218C00011000");
        list.add("O:CHPT220826C00016000");
        list.add("O:FCEL220311C00006000");
        list.add("O:CGC220527C00005500");
        list.add("O:NKLA220805C00007500");
        list.add("O:VALE220729C00013500");
        list.add("O:JMIA221118C00005500");
        list.add("O:SOFI221104C00006000");
        list.add("O:GOLD221104C00016000");
        list.add("O:BAC220715C00030500");
        list.add("O:AAL221021C00014500");
        list.add("O:NKLA220805C00007000");
        list.add("O:GOLD220218C00021000");
        list.add("O:AAL221021C00014000");
        list.add("O:AG220311C00013000");
        list.add("O:NIO221111C00010500");
        list.add("O:AAL221021C00014000");
        list.add("O:SPCE220218C00010500");
        list.add("O:XPEV221202C00007500");
        list.add("O:NCLH220812C00014000");
        list.add("O:AG220311C00013500");
        list.add("O:AMC220225C00015500");
        list.add("O:LAZR221104C00009000");
        list.add("O:XPEV221202C00007500");
        list.add("O:WBD221104C00013500");
        list.add("O:RKT220225C00012500");
        list.add("O:AAL220722C00015500");
        list.add("O:NCLH220225C00021000");
        list.add("O:SPCE220218C00011000");
        list.add("O:BBY220520C00080000");
        list.add("O:RUM221111C00013000");
        list.add("O:CLF221028C00016500");
        list.add("O:NCLH220225C00021000");
        list.add("O:SPWR220218C00017000");
        list.add("O:PINS220729C00018500");
        list.add("O:LAZR220225C00015000");
        list.add("O:HOOD221104C00012500");
        list.add("O:CPNG220513C00012000");
        list.add("O:MARA220225C00020000");
        list.add("O:LI220812C00032000");
        list.add("O:PLUG221111C00015500");
        list.add("O:MRO221104C00031500");
        list.add("O:CHPT220902C00015500");
        list.add("O:GME220902C00028500");
        list.add("O:PARA221104C00019000");
        list.add("O:NCLH220513C00018500");
        list.add("O:CPNG220513C00010500");
        list.add("O:GOOS221104C00018000");
        list.add("O:SNAP221021C00011500");
        list.add("O:GPS220527C00011000");
        list.add("O:SNAP221021C00011000");
        list.add("O:MGM221104C00037000");
        list.add("O:SAVA220805C00018000");
        list.add("O:WFC220715C00038500");
        list.add("O:FCX221021C00030000");
        list.add("O:LI221209C00023000");
        list.add("O:LI221209C00022500");
        list.add("O:LVS221021C00037000");
        list.add("O:CMA221021C00077500");
        list.add("O:X221028C00021000");
        list.add("O:NIO220610C00019500");
        list.add("O:U220805C00044000");
        list.add("O:C220715C00045000");
        list.add("O:PTON220826C00012000");
        list.add("O:DKNG221104C00017000");
        list.add("O:GPS220527C00011000");
        list.add("O:M221118C00021000");
        list.add("O:PDD220826C00049500");
        list.add("O:SNAP220722C00015000");
        list.add("O:LI221209C00024500");
        list.add("O:LVS220128C00044000");
        list.add("O:NVAX220805C00060000");
        list.add("O:BTU221104C00025000");
        list.add("O:BLNK220311C00025500");
        list.add("O:ABT221021C00107000");
        list.add("O:DKNG220218C00023000");
        list.add("O:JD221118C00053000");
        list.add("O:X220429C00033000");
        list.add("O:ABNB220211C00167500");
        list.add("O:MS221014C00076000");
        list.add("O:PLUG220812C00027000");
        list.add("O:TSM220715C00082000");
        list.add("O:ZM220819C00107000");
        list.add("O:BX221021C00092000");
        list.add("O:TSM220715C00081000");
        list.add("O:KSS220819C00034500");
        list.add("O:AA221021C00040000");
        list.add("O:TSM220414C00101000");
        list.add("O:RIVN220812C00037000");
        list.add("O:AFRM220513C00020000");
        list.add("O:SNAP220204C00035000");
        list.add("O:SNAP220422C00033500");
        list.add("O:AMAT221118C00112000");
        list.add("O:AFRM220513C00025000");
        list.add("O:BABA220805C00089000");
        list.add("O:AFRM220826C00030500");
        list.add("O:AFRM220826C00030000");
        list.add("O:BABA221118C00074000");
        list.add("O:BABA220527C00086000");
        list.add("O:NFLX221014C00215000");
        list.add("O:UPST220211C00109000");
        list.add("O:AMZN221028C00117000");
        list.add("O:AMZN220729C00117000");
        list.add("O:SQ220805C00082000");
        list.add("O:RIVN220311C00044000");
        list.add("O:DIS220513C00111000");
        list.add("O:CROX220218C00105000");
        list.add("O:COIN220506C00126000");
        list.add("O:BABA220225C00115000");
        list.add("O:ROKU221104C00057000");
        list.add("O:COIN221104C00070000");
        list.add("O:NVDA220826C00177500");
        list.add("O:MRNA221104C00157500");
        list.add("O:BA220128C00202500");
        list.add("O:BA220128C00202500");
        list.add("O:TTD220218C00080000");
        list.add("O:NVDA221118C00170000");
        list.add("O:RBLX220218C00069000");
        list.add("O:TGT220819C00180000");
        list.add("O:TGT221118C00180000");
        list.add("O:NVDA220527C00167500");
        list.add("O:NVDA220527C00165000");
        list.add("O:TSLA221021C00212500");
        list.add("O:TSLA221021C00232500");
        list.add("O:COIN220812C00099000");
        list.add("O:ROKU220218C00165000");
        list.add("O:NVDA220218C00252500");
        list.add("O:ROKU220218C00165000");
        list.add("O:KGC220218P00005000");
        list.add("O:BB220624P00005000");
        list.add("O:BB220624P00005000");
        list.add("O:FUBO220218P00010000");
        list.add("O:CHPT220826P00015000");
        list.add("O:FCEL220311P00005000");
        list.add("O:CGC220527P00004500");
        list.add("O:NKLA220805P00006500");
        list.add("O:VALE220729P00012500");
        list.add("O:JMIA221118P00004500");
        list.add("O:SOFI221104P00005000");
        list.add("O:GOLD221104P00015000");
        list.add("O:BAC220715P00029500");
        list.add("O:AAL221021P00013500");
        list.add("O:NKLA220805P00006000");
        list.add("O:GOLD220218P00020000");
        list.add("O:AAL221021P00013000");
        list.add("O:AG220311P00012000");
        list.add("O:NIO221111P00009500");
        list.add("O:AAL221021P00013000");
        list.add("O:SPCE220218P00010000");
        list.add("O:XPEV221202P00006500");
        list.add("O:NCLH220812P00013000");
        list.add("O:AG220311P00012500");
        list.add("O:AMC220225P00014500");
        list.add("O:LAZR221104P00008000");
        list.add("O:XPEV221202P00007000");
        list.add("O:WBD221104P00012500");
        list.add("O:RKT220225P00011500");
        list.add("O:AAL220722P00014500");
        list.add("O:NCLH220225P00020000");
        list.add("O:SPCE220218P00010000");
        list.add("O:BBY220520P00070000");
        list.add("O:RUM221111P00012000");
        list.add("O:CLF221028P00015500");
        list.add("O:NCLH220225P00020500");
        list.add("O:SPWR220218P00016000");
        list.add("O:PINS220729P00018000");
        list.add("O:LAZR220225P00014000");
        list.add("O:HOOD221104P00011500");
        list.add("O:CPNG220513P00011000");
        list.add("O:MARA220225P00019000");
        list.add("O:LI220812P00031000");
        list.add("O:PLUG221111P00014500");
        list.add("O:MRO221104P00030500");
        list.add("O:CHPT220902P00014500");
        list.add("O:GME220902P00027500");
        list.add("O:PARA221104P00018000");
        list.add("O:NCLH220513P00017500");
        list.add("O:CPNG220513P00009500");
        list.add("O:GOOS221104P00016000");
        list.add("O:SNAP221021P00010500");
        list.add("O:GPS220527P00010000");
        list.add("O:SNAP221021P00010500");
        list.add("O:MGM221104P00036000");
        list.add("O:SAVA220805P00017000");
        list.add("O:WFC220715P00037500");
        list.add("O:FCX221021P00029000");
        list.add("O:LI221209P00022000");
        list.add("O:LI221209P00021500");
        list.add("O:LVS221021P00036000");
        list.add("O:CMA221021P00072500");
        list.add("O:X221028P00020500");
        list.add("O:NIO220610P00018500");
        list.add("O:U220805P00043000");
        list.add("O:C220715P00044000");
        list.add("O:PTON220826P00011000");
        list.add("O:DKNG221104P00016500");
        list.add("O:GPS220527P00010000");
        list.add("O:M221118P00020500");
        list.add("O:PDD220826P00048500");
        list.add("O:SNAP220722P00014000");
        list.add("O:LI221209P00023500");
        list.add("O:LVS220128P00042000");
        list.add("O:NVAX220805P00058000");
        list.add("O:BTU221104P00024000");
        list.add("O:BLNK220311P00024500");
        list.add("O:ABT221021P00105000");
        list.add("O:DKNG220218P00022500");
        list.add("O:JD221118P00051000");
        list.add("O:X220429P00032000");
        list.add("O:ABNB220211P00162500");
        list.add("O:MS221014P00074000");
        list.add("O:PLUG220812P00026000");
        list.add("O:TSM220715P00080000");
        list.add("O:ZM220819P00105000");
        list.add("O:BX221021P00090000");
        list.add("O:TSM220715P00079000");
        list.add("O:KSS220819P00033500");
        list.add("O:AA221021P00039000");
        list.add("O:TSM220414P00099000");
        list.add("O:RIVN220812P00036000");
        list.add("O:AFRM220513P00018000");
        list.add("O:SNAP220204P00033000");
        list.add("O:SNAP220422P00032500");
        list.add("O:AMAT221118P00110000");
        list.add("O:AFRM220513P00024000");
        list.add("O:BABA220805P00087000");
        list.add("O:AFRM220826P00029500");
        list.add("O:AFRM220826P00029000");
        list.add("O:BABA221118P00072000");
        list.add("O:BABA220527P00084000");
        list.add("O:NFLX221014P00210000");
        list.add("O:UPST220211P00107000");
        list.add("O:AMZN221028P00115000");
        list.add("O:AMZN220729P00115000");
        list.add("O:SQ220805P00080000");
        list.add("O:RIVN220311P00043000");
        list.add("O:DIS220513P00109000");
        list.add("O:CROX220218P00095000");
        list.add("O:COIN220506P00124000");
        list.add("O:BABA220225P00113000");
        list.add("O:ROKU221104P00055000");
        list.add("O:COIN221104P00068000");
        list.add("O:NVDA220826P00172500");
        list.add("O:MRNA221104P00152500");
        list.add("O:BA220128P00197500");
        list.add("O:BA220128P00197500");
        list.add("O:TTD220218P00078000");
        list.add("O:NVDA221118P00165000");
        list.add("O:RBLX220218P00067000");
        list.add("O:TGT220819P00175000");
        list.add("O:TGT221118P00175000");
        list.add("O:NVDA220527P00162500");
        list.add("O:NVDA220527P00160000");
        list.add("O:TSLA221021P00207500");
        list.add("O:TSLA221021P00227500");
        list.add("O:COIN220812P00097000");
        list.add("O:ROKU220218P00160000");
        list.add("O:NVDA220218P00247500");
        list.add("O:ROKU220218P00162500");
        return list;
    }

    public static void main(String[] args) throws Exception {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.http").setLevel(Level.INFO);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("httpclient.wire").setLevel(Level.INFO);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.commons").setLevel(Level.ERROR);

        init();

        List<String> list = buildTestData();
        getOptionId(list);
        getHistoricalIV(getOptionCodeDateMap());
    }
}
