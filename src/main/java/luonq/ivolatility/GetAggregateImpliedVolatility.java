package luonq.ivolatility;

import bean.AggregateOptionIV;
import bean.AggregateOptionIVResp;
import com.alibaba.fastjson.JSON;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.httpclient.HttpClient;
import org.apache.commons.httpclient.methods.GetMethod;
import org.apache.commons.lang3.StringUtils;
import util.BaseUtils;
import util.Constants;

import java.io.InputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class GetAggregateImpliedVolatility {

    public static Map<String/* data */, Map<String/* optionCode */, Double/* IV */>> dateToIvMap = Maps.newHashMap();
    public static Map<String/* data */, Map<String/* optionCode */, String/* firstIvTime */>> dateToFirstIvTimeMap = Maps.newHashMap();
    public static HttpClient httpClient = new HttpClient();

    public static void init() throws Exception {
        Map<String, String> fileMap = BaseUtils.getFileMap(Constants.USER_PATH + "optionData/aggregateIV");

        String openTime = "09:32:00";
        for (String stock : fileMap.keySet()) {
            if (!stock.equals("FSLY")) {
                //                continue;
            }
            String dirPath = fileMap.get(stock);

            Map<String, String> optionDateMap = BaseUtils.getFileMap(dirPath);
            for (String date : optionDateMap.keySet()) {
                String optionFilePath = optionDateMap.get(date);

                Map<String, String> optionFileMap = BaseUtils.getFileMap(optionFilePath);
                for (String optionCode : optionFileMap.keySet()) {
                    List<String> lines = BaseUtils.readFile(optionFileMap.get(optionCode));
                    for (String firstLine : lines) {
                        String[] split = firstLine.split("\t");
                        if (split[1].contains("09:2")) {
                            continue;
                        }
                        String datetime = split[0];
                        date = datetime.substring(0, 10);
                        if (!dateToIvMap.containsKey(date)) {
                            dateToIvMap.put(date, Maps.newHashMap());
                        }
                        dateToIvMap.get(date).put(optionCode, Double.parseDouble(split[2]));
                    }


                    for (String firstLine : lines) {
                        String[] split = firstLine.split("\t");
                        if (split[1].contains("09:2")) {
                            continue;
                        }
                        if (!dateToFirstIvTimeMap.containsKey(date)) {
                            dateToFirstIvTimeMap.put(date, Maps.newHashMap());
                        }
                        dateToFirstIvTimeMap.get(date).put(optionCode, split[1]);
                        break;
                    }
                }
            }
        }
    }

    // https://restapi.ivolatility.com/equities/intraday/single-equity-option-rawiv?apiKey=S3j7pBefWG0J0glb&symbol=C&date=2024-01-11&expDate=2024-01-12&strike=53&optType=CALL&minuteType=MINUTE_1
    // https://restapi.ivolatility.com/equities/intraday/single-equity-optionsymbol-rawiv?apiKey=S3j7pBefWG0J0glb&optionSymbol=AAPL%20%20240503P00180000&date=2024-05-03&minuteType=MINUTE_1
    public static double getAggregateIv(String optionCode, String date) throws Exception {
        if (dateToIvMap.containsKey(date) && dateToIvMap.get(date).containsKey(optionCode)) {
            return dateToIvMap.get(date).get(optionCode);
        }

        String stock = optionCode.substring(0, optionCode.length() - 15);
        String expireDate = optionCode.substring(optionCode.length() - 15, optionCode.length() - 9);
        String expireDay = LocalDate.parse(expireDate, DateTimeFormatter.ofPattern("yyMMdd")).format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String type = optionCode.substring(optionCode.length() - 9, optionCode.length() - 8);
        String optionType = StringUtils.equalsIgnoreCase(type, "C") ? "CALL" : "PUT";
        String priceStr = optionCode.substring(optionCode.length() - 8);
        int priceInt = Integer.valueOf(priceStr);
        String strikePrice = BigDecimal.valueOf(priceInt).divide(BigDecimal.valueOf(1000)).setScale(1, RoundingMode.DOWN).toString();
        if (strikePrice.contains(".0")) {
            strikePrice = strikePrice.substring(0, strikePrice.length() - 2);
        }

        String optionFileDir = Constants.USER_PATH + "optionData/aggregateIV/" + stock + "/" + date + "/";
        String optionFilePath = optionFileDir + optionCode;
        List<String> lines = Lists.newArrayList();

        String url = String.format("https://restapi.ivolatility.com/equities/intraday/single-equity-option-rawiv?apiKey=S3j7pBefWG0J0glb&symbol=%s&date=%s&expDate=%s&strike=%s&optType=%s&minuteType=MINUTE_1", stock, date, expireDay, strikePrice, optionType);
        GetMethod get = new GetMethod(url);
        double hisIv = -2d;
        String marketClose = date + " 16:";
        String marketPre = date + " 09:29";
        try {
            httpClient.executeMethod(get);
            InputStream content = get.getResponseBodyAsStream();
            AggregateOptionIVResp resp = JSON.parseObject(content, AggregateOptionIVResp.class);
            List<AggregateOptionIV> dataList = resp.getData();
            if (CollectionUtils.isNotEmpty(dataList)) {
                for (AggregateOptionIV iv : dataList) {
                    String optionBidDateTime = iv.getOptionBidDateTime();
                    String timestamp = iv.getTimestamp();
                    String calcTimestamp = iv.getCalcTimestamp();
                    if (!optionBidDateTime.startsWith(date) || timestamp.startsWith(marketClose) || timestamp.startsWith(marketPre) || calcTimestamp.startsWith(marketPre)) {
                        continue;
                    }
                    double optionIv = iv.getOptionIv();
                    if (optionIv == -1) {
                        continue;
                    }
                    if (optionIv == 0) {
                        optionIv = -2d;
                    }
                    lines.add(timestamp + "\t" + calcTimestamp + "\t" + optionIv);
                }
            }
        } catch (Exception e) {
            System.out.println("error: " + url);
            return 0;
        } finally {
            get.releaseConnection();
        }

        System.out.println("getAggregateIv " + optionCode);
        if (CollectionUtils.isEmpty(lines)) {
            return hisIv;
        }
        BaseUtils.createDirectory(optionFileDir);
        Collections.sort(lines, (o1, o2) -> {
            Integer time1 = Integer.valueOf(o1.split("\t")[0].substring(11).replaceAll(":", ""));
            Integer time2 = Integer.valueOf(o2.split("\t")[0].substring(11).replaceAll(":", ""));
            return time1 - time2;
        });
        BaseUtils.writeFile(optionFilePath, lines);

        if (!dateToFirstIvTimeMap.containsKey(date)) {
            dateToFirstIvTimeMap.put(date, Maps.newHashMap());
        }
        String[] split = lines.get(0).split("\t");
        String firstDateTime = split[1];
        hisIv = Double.parseDouble(split[2]);
        dateToFirstIvTimeMap.get(date).put(optionCode, firstDateTime);

        return hisIv;
    }

    public static void main(String[] args) throws Exception {
//        getAggregateIv("BB220408P00006500", "2022-04-01");
        //        getAggregateIv("BB220408P00006500", "2022-04-01");
        //        getAggregateIv("SOFI230623C00009500", "2023-06-16");
        init();
    }
}
