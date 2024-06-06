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
        map.put("O:TAL230120C00008500", "2023-01-17");
        map.put("O:ALLY230120C00027000", "2023-01-19");
        map.put("O:FCX230127C00044000", "2023-01-24");
        map.put("O:SOFI230127C00006000", "2023-01-25");
        map.put("O:AMD230127C00077000", "2023-01-26");
        map.put("O:SNAP230127C00011000", "2023-01-26");
        map.put("O:MSTR230203C00255000", "2023-01-30");
        map.put("O:GM230203C00037000", "2023-01-30");
        map.put("O:F230203C00014000", "2023-01-31");
        map.put("O:ENPH230203C00235000", "2023-02-02");
        map.put("O:VFC230210C00031000", "2023-02-06");
        map.put("O:LYFT230210C00017000", "2023-02-06");
        map.put("O:FSLY230217C00012500", "2023-02-14");
        map.put("O:PARA230217C00023500", "2023-02-15");
        map.put("O:DASH230217C00064000", "2023-02-15");
        map.put("O:IQ230217C00008000", "2023-02-15");
        map.put("O:COIN230217C00067000", "2023-02-16");
        map.put("O:U230217C00041000", "2023-02-16");
        map.put("O:NVDA230217C00222500", "2023-02-16");
        map.put("O:IQ230217C00007500", "2023-02-16");
        map.put("O:RIG230217C00008000", "2023-02-16");
        map.put("O:WBD230224C00015500", "2023-02-21");
        map.put("O:SQ230224C00074000", "2023-02-21");
        map.put("O:MOS230224C00048500", "2023-02-21");
        map.put("O:CVNA230224C00011500", "2023-02-21");
        map.put("O:CVNA230224C00010500", "2023-02-22");
        map.put("O:RIVN230303C00018000", "2023-02-27");
        map.put("O:BLNK230303C00009500", "2023-02-27");
        map.put("O:CHPT230303C00011000", "2023-02-27");
        map.put("O:RIOT230303C00006500", "2023-02-27");
        map.put("O:M230303C00020500", "2023-03-01");
        map.put("O:RIOT230303C00007000", "2023-03-01");
        map.put("O:JD230310C00046000", "2023-03-08");
        map.put("O:MARA230317C00006000", "2023-03-13");
        map.put("O:FDX230317C00200000", "2023-03-13");
        map.put("O:STNE230317C00009000", "2023-03-13");
        map.put("O:PATH230317C00017500", "2023-03-14");
        map.put("O:MARA230317C00008000", "2023-03-14");
        map.put("O:XPEV230317C00008500", "2023-03-14");
        map.put("O:ADBE230317C00335000", "2023-03-14");
        map.put("O:XPEV230317C00008000", "2023-03-15");
        map.put("O:GRPN230317C00006000", "2023-03-15");
        map.put("O:ASTS230331C00006000", "2023-03-29");
        map.put("O:NFLX230414C00342500", "2023-04-13");
        map.put("O:BX230421C00091000", "2023-04-18");
        map.put("O:TSM230421C00087000", "2023-04-19");
        map.put("O:FCX230421C00042500", "2023-04-19");
        map.put("O:GM230421C00033500", "2023-04-20");
        map.put("O:X230428C00025000", "2023-04-25");
        map.put("O:SNAP230428C00011000", "2023-04-26");
        map.put("O:NET230428C00060000", "2023-04-26");
        map.put("O:AMZN230428C00106000", "2023-04-26");
        map.put("O:MSTR230428C00307500", "2023-04-27");
        map.put("O:AG230505C00007500", "2023-05-01");
        map.put("O:UBER230505C00032500", "2023-05-01");
        map.put("O:CVNA230505C00007500", "2023-05-01");
        map.put("O:MRO230505C00024000", "2023-05-02");
        map.put("O:CVNA230505C00007000", "2023-05-02");
        map.put("O:WW230505C00008500", "2023-05-03");
        map.put("O:DVN230505C00050000", "2023-05-03");
        map.put("O:MARA230512C00011000", "2023-05-08");
        map.put("O:RIOT230512C00011000", "2023-05-08");
        map.put("O:DVN230512C00053000", "2023-05-08");
        map.put("O:LI230512C00025000", "2023-05-08");
        map.put("O:MARA230512C00011000", "2023-05-10");
        map.put("O:JD230512C00035000", "2023-05-10");
        map.put("O:BABA230519C00088000", "2023-05-15");
        map.put("O:BIDU230519C00125000", "2023-05-15");
        map.put("O:IQ230519C00006500", "2023-05-15");
        map.put("O:XPEV230526C00009500", "2023-05-22");
        map.put("O:XPEV230526C00009500", "2023-05-23");
        map.put("O:PDD230526C00062000", "2023-05-23");
        map.put("O:GPS230526C00008500", "2023-05-24");
        map.put("O:MRVL230526C00046000", "2023-05-24");
        map.put("O:ASAN230602C00022500", "2023-05-30");
        map.put("O:CRWD230602C00160000", "2023-05-30");
        map.put("O:CHPT230602C00009500", "2023-05-30");
        map.put("O:CHWY230602C00031500", "2023-05-30");
        map.put("O:ADBE230616C00465000", "2023-06-12");
        map.put("O:FCX230721C00040500", "2023-07-17");
        map.put("O:JBLU230728C00008000", "2023-07-27");
        map.put("O:PBR230804C00014500", "2023-07-31");
        map.put("O:COIN230804C00097000", "2023-08-01");
        map.put("O:WW230804C00012000", "2023-08-01");
        map.put("O:COIN230804C00092000", "2023-08-02");
        map.put("O:CHPT230804C00008500", "2023-08-02");
        map.put("O:RKT230804C00011000", "2023-08-02");
        map.put("O:BYND230804C00017000", "2023-08-02");
        map.put("O:SQ230804C00078000", "2023-08-02");
        map.put("O:PLTR230804C00019500", "2023-08-02");
        map.put("O:OPEN230804C00005500", "2023-08-02");
        map.put("O:LCID230804C00007500", "2023-08-02");
        map.put("O:RIVN230804C00025500", "2023-08-03");
        map.put("O:AMC230811C00005500", "2023-08-07");
        map.put("O:BABA230811C00094000", "2023-08-08");
        map.put("O:PLUG230811C00011000", "2023-08-08");
        map.put("O:RIOT230811C00017000", "2023-08-08");
        map.put("O:SE230811C00058000", "2023-08-10");
        map.put("O:COHR230818C00050000", "2023-08-14");
        map.put("O:XPEV230818C00016000", "2023-08-16");
        map.put("O:BILI230818C00015500", "2023-08-16");
        map.put("O:XPEV230818C00017000", "2023-08-17");
        map.put("O:NVDA230825C00447500", "2023-08-21");
        map.put("O:NVDA230825C00482500", "2023-08-22");
        map.put("O:CRWD230901C00147000", "2023-08-28");
        map.put("O:NIO230901C00011500", "2023-08-28");
        map.put("O:ADBE230915C00560000", "2023-09-13");
        map.put("O:CCL230929C00014000", "2023-09-28");
        map.put("O:DAL231013C00036000", "2023-10-09");
        map.put("O:SPOT231020C00155000", "2023-10-19");
        map.put("O:NLY231027C00016000", "2023-10-24");
        map.put("O:PINS231027C00025500", "2023-10-26");
        map.put("O:RIVN231103C00017000", "2023-11-02");
        map.put("O:PARA231103C00011500", "2023-11-02");
        map.put("O:DDOG231103C00083000", "2023-11-02");
        map.put("O:CPNG231103C00016500", "2023-11-02");
        map.put("O:RIOT231103C00011000", "2023-11-02");
        map.put("O:AMC231110C00011500", "2023-11-06");
        map.put("O:RBLX231110C00036000", "2023-11-06");
        map.put("O:UPST231110C00031500", "2023-11-06");
        map.put("O:U231110C00028500", "2023-11-06");
        map.put("O:NIO231110C00009000", "2023-11-06");
        map.put("O:RIOT231110C00012000", "2023-11-06");
        map.put("O:AFRM231110C00022000", "2023-11-08");
        map.put("O:U231110C00027500", "2023-11-09");
        map.put("O:XPEV231117C00017000", "2023-11-14");
        map.put("O:AMAT231117C00155000", "2023-11-14");
        map.put("O:GPS231117C00014000", "2023-11-14");
        map.put("O:M231117C00011500", "2023-11-14");
        map.put("O:ZIM231117C00007500", "2023-11-14");
        map.put("O:BABA231117C00088000", "2023-11-15");
        map.put("O:M231117C00012500", "2023-11-15");
        map.put("O:BIDU231117C00109000", "2023-11-16");
        map.put("O:IQ231124C00005500", "2023-11-20");
        map.put("O:JWN231124C00015000", "2023-11-21");
        map.put("O:GME231208C00017000", "2023-12-05");
        map.put("O:GME231208C00015500", "2023-12-06");
        map.put("O:TAL230120P00008000", "2023-01-17");
        map.put("O:ALLY230120P00025000", "2023-01-19");
        map.put("O:FCX230127P00043500", "2023-01-24");
        map.put("O:SOFI230127P00005500", "2023-01-25");
        map.put("O:AMD230127P00076000", "2023-01-26");
        map.put("O:SNAP230127P00010000", "2023-01-26");
        map.put("O:MSTR230203P00250000", "2023-01-30");
        map.put("O:GM230203P00036500", "2023-01-30");
        map.put("O:F230203P00013000", "2023-01-31");
        map.put("O:ENPH230203P00230000", "2023-02-02");
        map.put("O:VFC230210P00029000", "2023-02-06");
        map.put("O:LYFT230210P00016500", "2023-02-06");
        map.put("O:FSLY230217P00012000", "2023-02-14");
        map.put("O:PARA230217P00022500", "2023-02-15");
        map.put("O:DASH230217P00062000", "2023-02-15");
        map.put("O:IQ230217P00007000", "2023-02-15");
        map.put("O:COIN230217P00065000", "2023-02-16");
        map.put("O:U230217P00040500", "2023-02-16");
        map.put("O:NVDA230217P00220000", "2023-02-16");
        map.put("O:IQ230217P00007000", "2023-02-16");
        map.put("O:RIG230217P00007000", "2023-02-16");
        map.put("O:WBD230224P00014500", "2023-02-21");
        map.put("O:SQ230224P00072000", "2023-02-21");
        map.put("O:MOS230224P00048000", "2023-02-21");
        map.put("O:CVNA230224P00010500", "2023-02-21");
        map.put("O:CVNA230224P00010000", "2023-02-22");
        map.put("O:RIVN230303P00017500", "2023-02-27");
        map.put("O:BLNK230303P00009000", "2023-02-27");
        map.put("O:CHPT230303P00010500", "2023-02-27");
        map.put("O:RIOT230303P00005500", "2023-02-27");
        map.put("O:M230303P00019500", "2023-03-01");
        map.put("O:RIOT230303P00006000", "2023-03-01");
        map.put("O:JD230310P00045000", "2023-03-08");
        map.put("O:MARA230317P00005500", "2023-03-13");
        map.put("O:FDX230317P00195000", "2023-03-13");
        map.put("O:STNE230317P00008000", "2023-03-13");
        map.put("O:PATH230317P00012500", "2023-03-14");
        map.put("O:MARA230317P00007000", "2023-03-14");
        map.put("O:XPEV230317P00007500", "2023-03-14");
        map.put("O:ADBE230317P00330000", "2023-03-14");
        map.put("O:XPEV230317P00007500", "2023-03-15");
        map.put("O:GRPN230317P00005000", "2023-03-15");
        map.put("O:ASTS230331P00005500", "2023-03-29");
        map.put("O:NFLX230414P00337500", "2023-04-13");
        map.put("O:BX230421P00090000", "2023-04-18");
        map.put("O:TSM230421P00085000", "2023-04-19");
        map.put("O:FCX230421P00041500", "2023-04-19");
        map.put("O:GM230421P00033000", "2023-04-20");
        map.put("O:X230428P00024500", "2023-04-25");
        map.put("O:SNAP230428P00010000", "2023-04-26");
        map.put("O:NET230428P00059000", "2023-04-26");
        map.put("O:AMZN230428P00104000", "2023-04-26");
        map.put("O:MSTR230428P00305000", "2023-04-27");
        map.put("O:AG230505P00007000", "2023-05-01");
        map.put("O:UBER230505P00031500", "2023-05-01");
        map.put("O:CVNA230505P00007000", "2023-05-01");
        map.put("O:MRO230505P00023000", "2023-05-02");
        map.put("O:CVNA230505P00006500", "2023-05-02");
        map.put("O:WW230505P00008000", "2023-05-03");
        map.put("O:DVN230505P00049000", "2023-05-03");
        map.put("O:MARA230512P00010000", "2023-05-08");
        map.put("O:RIOT230512P00010500", "2023-05-08");
        map.put("O:DVN230512P00051000", "2023-05-08");
        map.put("O:LI230512P00024500", "2023-05-08");
        map.put("O:MARA230512P00010000", "2023-05-10");
        map.put("O:JD230512P00034000", "2023-05-10");
        map.put("O:BABA230519P00086000", "2023-05-15");
        map.put("O:BIDU230519P00123000", "2023-05-15");
        map.put("O:IQ230519P00005500", "2023-05-15");
        map.put("O:XPEV230526P00009000", "2023-05-22");
        map.put("O:XPEV230526P00009000", "2023-05-23");
        map.put("O:PDD230526P00061000", "2023-05-23");
        map.put("O:GPS230526P00007500", "2023-05-24");
        map.put("O:MRVL230526P00045000", "2023-05-24");
        map.put("O:ASAN230602P00021500", "2023-05-30");
        map.put("O:CRWD230602P00157500", "2023-05-30");
        map.put("O:CHPT230602P00009000", "2023-05-30");
        map.put("O:CHWY230602P00031000", "2023-05-30");
        map.put("O:ADBE230616P00460000", "2023-06-12");
        map.put("O:FCX230721P00039500", "2023-07-17");
        map.put("O:JBLU230728P00007500", "2023-07-27");
        map.put("O:PBR230804P00014000", "2023-07-31");
        map.put("O:COIN230804P00095000", "2023-08-01");
        map.put("O:WW230804P00011000", "2023-08-01");
        map.put("O:COIN230804P00091000", "2023-08-02");
        map.put("O:CHPT230804P00008000", "2023-08-02");
        map.put("O:RKT230804P00010000", "2023-08-02");
        map.put("O:BYND230804P00016000", "2023-08-02");
        map.put("O:SQ230804P00076000", "2023-08-02");
        map.put("O:PLTR230804P00019000", "2023-08-02");
        map.put("O:OPEN230804P00004500", "2023-08-02");
        map.put("O:LCID230804P00006500", "2023-08-02");
        map.put("O:RIVN230804P00025000", "2023-08-03");
        map.put("O:AMC230811P00004500", "2023-08-07");
        map.put("O:BABA230811P00093000", "2023-08-08");
        map.put("O:PLUG230811P00010500", "2023-08-08");
        map.put("O:RIOT230811P00016500", "2023-08-08");
        map.put("O:SE230811P00057000", "2023-08-10");
        map.put("O:COHR230818P00040000", "2023-08-14");
        map.put("O:XPEV230818P00015000", "2023-08-16");
        map.put("O:BILI230818P00015000", "2023-08-16");
        map.put("O:XPEV230818P00016000", "2023-08-17");
        map.put("O:NVDA230825P00442500", "2023-08-21");
        map.put("O:NVDA230825P00480000", "2023-08-22");
        map.put("O:CRWD230901P00145000", "2023-08-28");
        map.put("O:NIO230901P00010500", "2023-08-28");
        map.put("O:ADBE230915P00555000", "2023-09-13");
        map.put("O:CCL230929P00013500", "2023-09-28");
        map.put("O:DAL231013P00035500", "2023-10-09");
        map.put("O:SPOT231020P00150000", "2023-10-19");
        map.put("O:NLY231027P00015500", "2023-10-24");
        map.put("O:PINS231027P00024500", "2023-10-26");
        map.put("O:RIVN231103P00016500", "2023-11-02");
        map.put("O:PARA231103P00010500", "2023-11-02");
        map.put("O:DDOG231103P00081000", "2023-11-02");
        map.put("O:CPNG231103P00016000", "2023-11-02");
        map.put("O:RIOT231103P00010000", "2023-11-02");
        map.put("O:AMC231110P00010500", "2023-11-06");
        map.put("O:RBLX231110P00035000", "2023-11-06");
        map.put("O:UPST231110P00030500", "2023-11-06");
        map.put("O:U231110P00028000", "2023-11-06");
        map.put("O:NIO231110P00008000", "2023-11-06");
        map.put("O:RIOT231110P00011500", "2023-11-06");
        map.put("O:AFRM231110P00021000", "2023-11-08");
        map.put("O:U231110P00026500", "2023-11-09");
        map.put("O:XPEV231117P00016000", "2023-11-14");
        map.put("O:AMAT231117P00150000", "2023-11-14");
        map.put("O:GPS231117P00013500", "2023-11-14");
        map.put("O:M231117P00010500", "2023-11-14");
        map.put("O:ZIM231117P00007000", "2023-11-14");
        map.put("O:BABA231117P00086000", "2023-11-15");
        map.put("O:M231117P00011500", "2023-11-15");
        map.put("O:BIDU231117P00108000", "2023-11-16");
        map.put("O:IQ231124P00005000", "2023-11-20");
        map.put("O:JWN231124P00014500", "2023-11-21");
        map.put("O:GME231208P00016000", "2023-12-05");
        map.put("O:GME231208P00015000", "2023-12-06");
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
        list.add("O:TAL230120C00008500");
        list.add("O:ALLY230120C00027000");
        list.add("O:FCX230127C00044000");
        list.add("O:SOFI230127C00006000");
        list.add("O:AMD230127C00077000");
        list.add("O:SNAP230127C00011000");
        list.add("O:MSTR230203C00255000");
        list.add("O:GM230203C00037000");
        list.add("O:F230203C00014000");
        list.add("O:ENPH230203C00235000");
        list.add("O:VFC230210C00031000");
        list.add("O:LYFT230210C00017000");
        list.add("O:FSLY230217C00012500");
        list.add("O:PARA230217C00023500");
        list.add("O:DASH230217C00064000");
        list.add("O:IQ230217C00008000");
        list.add("O:COIN230217C00067000");
        list.add("O:U230217C00041000");
        list.add("O:NVDA230217C00222500");
        list.add("O:IQ230217C00007500");
        list.add("O:RIG230217C00008000");
        list.add("O:WBD230224C00015500");
        list.add("O:SQ230224C00074000");
        list.add("O:MOS230224C00048500");
        list.add("O:CVNA230224C00011500");
        list.add("O:CVNA230224C00010500");
        list.add("O:RIVN230303C00018000");
        list.add("O:BLNK230303C00009500");
        list.add("O:CHPT230303C00011000");
        list.add("O:RIOT230303C00006500");
        list.add("O:M230303C00020500");
        list.add("O:RIOT230303C00007000");
        list.add("O:JD230310C00046000");
        list.add("O:MARA230317C00006000");
        list.add("O:FDX230317C00200000");
        list.add("O:STNE230317C00009000");
        list.add("O:PATH230317C00017500");
        list.add("O:MARA230317C00008000");
        list.add("O:XPEV230317C00008500");
        list.add("O:ADBE230317C00335000");
        list.add("O:XPEV230317C00008000");
        list.add("O:GRPN230317C00006000");
        list.add("O:ASTS230331C00006000");
        list.add("O:NFLX230414C00342500");
        list.add("O:BX230421C00091000");
        list.add("O:TSM230421C00087000");
        list.add("O:FCX230421C00042500");
        list.add("O:GM230421C00033500");
        list.add("O:X230428C00025000");
        list.add("O:SNAP230428C00011000");
        list.add("O:NET230428C00060000");
        list.add("O:AMZN230428C00106000");
        list.add("O:MSTR230428C00307500");
        list.add("O:AG230505C00007500");
        list.add("O:UBER230505C00032500");
        list.add("O:CVNA230505C00007500");
        list.add("O:MRO230505C00024000");
        list.add("O:CVNA230505C00007000");
        list.add("O:WW230505C00008500");
        list.add("O:DVN230505C00050000");
        list.add("O:MARA230512C00011000");
        list.add("O:RIOT230512C00011000");
        list.add("O:DVN230512C00053000");
        list.add("O:LI230512C00025000");
        list.add("O:MARA230512C00011000");
        list.add("O:JD230512C00035000");
        list.add("O:BABA230519C00088000");
        list.add("O:BIDU230519C00125000");
        list.add("O:IQ230519C00006500");
        list.add("O:XPEV230526C00009500");
        list.add("O:XPEV230526C00009500");
        list.add("O:PDD230526C00062000");
        list.add("O:GPS230526C00008500");
        list.add("O:MRVL230526C00046000");
        list.add("O:ASAN230602C00022500");
        list.add("O:CRWD230602C00160000");
        list.add("O:CHPT230602C00009500");
        list.add("O:CHWY230602C00031500");
        list.add("O:ADBE230616C00465000");
        list.add("O:FCX230721C00040500");
        list.add("O:JBLU230728C00008000");
        list.add("O:PBR230804C00014500");
        list.add("O:COIN230804C00097000");
        list.add("O:WW230804C00012000");
        list.add("O:COIN230804C00092000");
        list.add("O:CHPT230804C00008500");
        list.add("O:RKT230804C00011000");
        list.add("O:BYND230804C00017000");
        list.add("O:SQ230804C00078000");
        list.add("O:PLTR230804C00019500");
        list.add("O:OPEN230804C00005500");
        list.add("O:LCID230804C00007500");
        list.add("O:RIVN230804C00025500");
        list.add("O:AMC230811C00005500");
        list.add("O:BABA230811C00094000");
        list.add("O:PLUG230811C00011000");
        list.add("O:RIOT230811C00017000");
        list.add("O:SE230811C00058000");
        list.add("O:COHR230818C00050000");
        list.add("O:XPEV230818C00016000");
        list.add("O:BILI230818C00015500");
        list.add("O:XPEV230818C00017000");
        list.add("O:NVDA230825C00447500");
        list.add("O:NVDA230825C00482500");
        list.add("O:CRWD230901C00147000");
        list.add("O:NIO230901C00011500");
        list.add("O:ADBE230915C00560000");
        list.add("O:CCL230929C00014000");
        list.add("O:DAL231013C00036000");
        list.add("O:SPOT231020C00155000");
        list.add("O:NLY231027C00016000");
        list.add("O:PINS231027C00025500");
        list.add("O:RIVN231103C00017000");
        list.add("O:PARA231103C00011500");
        list.add("O:DDOG231103C00083000");
        list.add("O:CPNG231103C00016500");
        list.add("O:RIOT231103C00011000");
        list.add("O:AMC231110C00011500");
        list.add("O:RBLX231110C00036000");
        list.add("O:UPST231110C00031500");
        list.add("O:U231110C00028500");
        list.add("O:NIO231110C00009000");
        list.add("O:RIOT231110C00012000");
        list.add("O:AFRM231110C00022000");
        list.add("O:U231110C00027500");
        list.add("O:XPEV231117C00017000");
        list.add("O:AMAT231117C00155000");
        list.add("O:GPS231117C00014000");
        list.add("O:M231117C00011500");
        list.add("O:ZIM231117C00007500");
        list.add("O:BABA231117C00088000");
        list.add("O:M231117C00012500");
        list.add("O:BIDU231117C00109000");
        list.add("O:IQ231124C00005500");
        list.add("O:JWN231124C00015000");
        list.add("O:GME231208C00017000");
        list.add("O:GME231208C00015500");
        list.add("O:TAL230120P00008000");
        list.add("O:ALLY230120P00025000");
        list.add("O:FCX230127P00043500");
        list.add("O:SOFI230127P00005500");
        list.add("O:AMD230127P00076000");
        list.add("O:SNAP230127P00010000");
        list.add("O:MSTR230203P00250000");
        list.add("O:GM230203P00036500");
        list.add("O:F230203P00013000");
        list.add("O:ENPH230203P00230000");
        list.add("O:VFC230210P00029000");
        list.add("O:LYFT230210P00016500");
        list.add("O:FSLY230217P00012000");
        list.add("O:PARA230217P00022500");
        list.add("O:DASH230217P00062000");
        list.add("O:IQ230217P00007000");
        list.add("O:COIN230217P00065000");
        list.add("O:U230217P00040500");
        list.add("O:NVDA230217P00220000");
        list.add("O:IQ230217P00007000");
        list.add("O:RIG230217P00007000");
        list.add("O:WBD230224P00014500");
        list.add("O:SQ230224P00072000");
        list.add("O:MOS230224P00048000");
        list.add("O:CVNA230224P00010500");
        list.add("O:CVNA230224P00010000");
        list.add("O:RIVN230303P00017500");
        list.add("O:BLNK230303P00009000");
        list.add("O:CHPT230303P00010500");
        list.add("O:RIOT230303P00005500");
        list.add("O:M230303P00019500");
        list.add("O:RIOT230303P00006000");
        list.add("O:JD230310P00045000");
        list.add("O:MARA230317P00005500");
        list.add("O:FDX230317P00195000");
        list.add("O:STNE230317P00008000");
        list.add("O:PATH230317P00012500");
        list.add("O:MARA230317P00007000");
        list.add("O:XPEV230317P00007500");
        list.add("O:ADBE230317P00330000");
        list.add("O:XPEV230317P00007500");
        list.add("O:GRPN230317P00005000");
        list.add("O:ASTS230331P00005500");
        list.add("O:NFLX230414P00337500");
        list.add("O:BX230421P00090000");
        list.add("O:TSM230421P00085000");
        list.add("O:FCX230421P00041500");
        list.add("O:GM230421P00033000");
        list.add("O:X230428P00024500");
        list.add("O:SNAP230428P00010000");
        list.add("O:NET230428P00059000");
        list.add("O:AMZN230428P00104000");
        list.add("O:MSTR230428P00305000");
        list.add("O:AG230505P00007000");
        list.add("O:UBER230505P00031500");
        list.add("O:CVNA230505P00007000");
        list.add("O:MRO230505P00023000");
        list.add("O:CVNA230505P00006500");
        list.add("O:WW230505P00008000");
        list.add("O:DVN230505P00049000");
        list.add("O:MARA230512P00010000");
        list.add("O:RIOT230512P00010500");
        list.add("O:DVN230512P00051000");
        list.add("O:LI230512P00024500");
        list.add("O:MARA230512P00010000");
        list.add("O:JD230512P00034000");
        list.add("O:BABA230519P00086000");
        list.add("O:BIDU230519P00123000");
        list.add("O:IQ230519P00005500");
        list.add("O:XPEV230526P00009000");
        list.add("O:XPEV230526P00009000");
        list.add("O:PDD230526P00061000");
        list.add("O:GPS230526P00007500");
        list.add("O:MRVL230526P00045000");
        list.add("O:ASAN230602P00021500");
        list.add("O:CRWD230602P00157500");
        list.add("O:CHPT230602P00009000");
        list.add("O:CHWY230602P00031000");
        list.add("O:ADBE230616P00460000");
        list.add("O:FCX230721P00039500");
        list.add("O:JBLU230728P00007500");
        list.add("O:PBR230804P00014000");
        list.add("O:COIN230804P00095000");
        list.add("O:WW230804P00011000");
        list.add("O:COIN230804P00091000");
        list.add("O:CHPT230804P00008000");
        list.add("O:RKT230804P00010000");
        list.add("O:BYND230804P00016000");
        list.add("O:SQ230804P00076000");
        list.add("O:PLTR230804P00019000");
        list.add("O:OPEN230804P00004500");
        list.add("O:LCID230804P00006500");
        list.add("O:RIVN230804P00025000");
        list.add("O:AMC230811P00004500");
        list.add("O:BABA230811P00093000");
        list.add("O:PLUG230811P00010500");
        list.add("O:RIOT230811P00016500");
        list.add("O:SE230811P00057000");
        list.add("O:COHR230818P00040000");
        list.add("O:XPEV230818P00015000");
        list.add("O:BILI230818P00015000");
        list.add("O:XPEV230818P00016000");
        list.add("O:NVDA230825P00442500");
        list.add("O:NVDA230825P00480000");
        list.add("O:CRWD230901P00145000");
        list.add("O:NIO230901P00010500");
        list.add("O:ADBE230915P00555000");
        list.add("O:CCL230929P00013500");
        list.add("O:DAL231013P00035500");
        list.add("O:SPOT231020P00150000");
        list.add("O:NLY231027P00015500");
        list.add("O:PINS231027P00024500");
        list.add("O:RIVN231103P00016500");
        list.add("O:PARA231103P00010500");
        list.add("O:DDOG231103P00081000");
        list.add("O:CPNG231103P00016000");
        list.add("O:RIOT231103P00010000");
        list.add("O:AMC231110P00010500");
        list.add("O:RBLX231110P00035000");
        list.add("O:UPST231110P00030500");
        list.add("O:U231110P00028000");
        list.add("O:NIO231110P00008000");
        list.add("O:RIOT231110P00011500");
        list.add("O:AFRM231110P00021000");
        list.add("O:U231110P00026500");
        list.add("O:XPEV231117P00016000");
        list.add("O:AMAT231117P00150000");
        list.add("O:GPS231117P00013500");
        list.add("O:M231117P00010500");
        list.add("O:ZIM231117P00007000");
        list.add("O:BABA231117P00086000");
        list.add("O:M231117P00011500");
        list.add("O:BIDU231117P00108000");
        list.add("O:IQ231124P00005000");
        list.add("O:JWN231124P00014500");
        list.add("O:GME231208P00016000");
        list.add("O:GME231208P00015000");
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
