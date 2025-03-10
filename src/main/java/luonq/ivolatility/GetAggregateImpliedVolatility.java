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
    public static Map<String/* data */, Map<String/* optionCode */, List<Double>/* IV */>> dateToIvListMap = Maps.newHashMap();
    public static Map<String/* data */, Map<String/* optionCode */, List<String>/* firstIvTime */>> dateToFirstIvTimeListMap = Maps.newHashMap();
    public static Map<String/* data */, Map<String/* optionCode */, List<AggregateOptionIV>/* greek info */>> dateToGreekListMap = Maps.newHashMap();
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
                    boolean flag = true;
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
                        if (flag) {
                            flag = false;
                            continue;
                        }
                        dateToIvMap.get(date).put(optionCode, Double.parseDouble(split[2]));
                        flag = true;
                        break;
                    }

                    for (String firstLine : lines) {
                        String[] split = firstLine.split("\t");
                        if (split[1].contains("09:2")) {
                            continue;
                        }
                        if (!dateToFirstIvTimeMap.containsKey(date)) {
                            dateToFirstIvTimeMap.put(date, Maps.newHashMap());
                        }
                        if (flag) {
                            flag = false;
                            continue;
                        }
                        dateToFirstIvTimeMap.get(date).put(optionCode, split[1]);
                        flag = true;

                        break;
                    }

                    for (String firstLine : lines) {
                        String[] split = firstLine.split("\t");
                        if (split[1].contains("09:2")) {
                            continue;
                        }

                        if (!dateToIvListMap.containsKey(date)) {
                            dateToIvListMap.put(date, Maps.newHashMap());
                        }
                        if (!dateToIvListMap.get(date).containsKey(optionCode)) {
                            dateToIvListMap.get(date).put(optionCode, Lists.newArrayList());
                        }
                        dateToIvListMap.get(date).get(optionCode).add(Double.parseDouble(split[2]));

                        if (!dateToFirstIvTimeListMap.containsKey(date)) {
                            dateToFirstIvTimeListMap.put(date, Maps.newHashMap());
                        }
                        if (!dateToFirstIvTimeListMap.get(date).containsKey(optionCode)) {
                            dateToFirstIvTimeListMap.get(date).put(optionCode, Lists.newArrayList());
                        }
                        dateToFirstIvTimeListMap.get(date).get(optionCode).add(split[1]);

                        if (split.length >= 7) {
                            double iv = Double.parseDouble(split[2]);
                            double delta = Double.parseDouble(split[3]);
                            double gamma = Double.parseDouble(split[4]);
                            double theta = Double.parseDouble(split[5]);
                            double vega = Double.parseDouble(split[6]);
                            AggregateOptionIV aggregateOptionIV = new AggregateOptionIV();
                            aggregateOptionIV.setOptionSymbol(optionCode);
                            aggregateOptionIV.setOptionIv(iv);
                            aggregateOptionIV.setOptionDelta(delta);
                            aggregateOptionIV.setOptionGamma(gamma);
                            aggregateOptionIV.setOptionTheta(theta);
                            aggregateOptionIV.setOptionVega(vega);

                            if (!dateToGreekListMap.containsKey(date)) {
                                dateToGreekListMap.put(date, Maps.newHashMap());
                            }
                            if (!dateToGreekListMap.get(date).containsKey(optionCode)) {
                                dateToGreekListMap.get(date).put(optionCode, Lists.newArrayList());
                            }
                            dateToGreekListMap.get(date).get(optionCode).add(aggregateOptionIV);
                        }
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
                    double optionDelta = iv.getOptionDelta();
                    double optionGamma = iv.getOptionGamma();
                    double optionVega = iv.getOptionVega();
                    double optionTheta = iv.getOptionTheta();
                    if (optionIv == -1) {
                        continue;
                    }
                    if (optionIv == 0) {
                        optionIv = -2d;
                    }
                    lines.add(timestamp + "\t" + calcTimestamp + "\t" + optionIv + "\t" + optionDelta + "\t" + optionGamma + "\t" + optionTheta + "\t" + optionVega);
                }
            }
        } catch (Exception e) {
            System.out.println("error: " + url);
            return 0;
        } finally {
            get.releaseConnection();
        }

        if (CollectionUtils.isEmpty(lines)) {
            System.out.println("getAggregateIv is empty " + optionCode);
            return hisIv;
        }
        System.out.println("getAggregateIv " + optionCode);

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
        String[] split = lines.get(1).split("\t");
        String firstDateTime = split[1];
        hisIv = Double.parseDouble(split[2]);
        dateToFirstIvTimeMap.get(date).put(optionCode, firstDateTime);

        return hisIv;
    }

    public static List<Double> getAggregateIvList(String optionCode, String date) throws Exception {
        //        if (dateToIvListMap.containsKey(date) && dateToIvListMap.get(date).containsKey(optionCode)) {
        //            return dateToIvListMap.get(date).get(optionCode);
        //        }

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

        if (!dateToFirstIvTimeListMap.containsKey(date)) {
            dateToFirstIvTimeListMap.put(date, Maps.newHashMap());
        }
        if (!dateToFirstIvTimeListMap.get(date).containsKey(optionCode)) {
            dateToFirstIvTimeListMap.get(date).put(optionCode, Lists.newArrayList());
        }
        List<String> timeList = dateToFirstIvTimeListMap.get(date).get(optionCode);
        List<String> tempTimeList = Lists.newArrayList();

        if (!dateToIvListMap.containsKey(date)) {
            dateToIvListMap.put(date, Maps.newHashMap());
        }
        if (!dateToIvListMap.get(date).containsKey(optionCode)) {
            dateToIvListMap.get(date).put(optionCode, Lists.newArrayList());
        }
        List<Double> ivList = dateToIvListMap.get(date).get(optionCode);
        List<Double> tempIvList = Lists.newArrayList();

        if (!dateToGreekListMap.containsKey(date)) {
            dateToGreekListMap.put(date, Maps.newHashMap());
        }
        if (!dateToGreekListMap.get(date).containsKey(optionCode)) {
            dateToGreekListMap.get(date).put(optionCode, Lists.newArrayList());
        }
        List<AggregateOptionIV> aggrIVList = dateToGreekListMap.get(date).get(optionCode);
        List<AggregateOptionIV> tempAggrIVList = Lists.newArrayList();
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
                    double optionDelta = iv.getOptionDelta();
                    double optionGamma = iv.getOptionGamma();
                    double optionVega = iv.getOptionVega();
                    double optionTheta = iv.getOptionTheta();
                    if (optionIv == -1 || optionDelta == 0 || optionGamma == 0 || optionTheta == 0 || optionVega == 0) {
                        //                        continue;
                        System.out.println("getAggregateIv empty for" + optionCode);
                        return Lists.newArrayListWithExpectedSize(0);
                    }
                    if (optionIv == 0) {
                        optionIv = -2d;
                    }
                    lines.add(timestamp + "\t" + calcTimestamp + "\t" + optionIv + "\t" + optionDelta + "\t" + optionGamma + "\t" + optionTheta + "\t" + optionVega);
                    tempTimeList.add(calcTimestamp);
                    tempIvList.add(optionIv);

                    AggregateOptionIV aggregateOptionIV = new AggregateOptionIV();
                    aggregateOptionIV.setOptionSymbol(optionCode);
                    aggregateOptionIV.setOptionIv(optionIv);
                    aggregateOptionIV.setOptionDelta(optionDelta);
                    aggregateOptionIV.setOptionGamma(optionGamma);
                    aggregateOptionIV.setOptionTheta(optionTheta);
                    aggregateOptionIV.setOptionVega(optionVega);
                    tempAggrIVList.add(aggregateOptionIV);
                }
            }
        } catch (Exception e) {
            System.out.println("error: " + url);
            return Lists.newArrayList();
        } finally {
            get.releaseConnection();
        }

        ivList.addAll(tempIvList);
        timeList.addAll(tempTimeList);
        aggrIVList.addAll(tempAggrIVList);

        if (CollectionUtils.isEmpty(lines)) {
            System.out.println("getAggregateIv is empty " + optionCode);
            return Lists.newArrayList(hisIv);
        }
        System.out.println("getAggregateIv " + optionCode);

        BaseUtils.createDirectory(optionFileDir);
        Collections.sort(lines, (o1, o2) -> {
            Integer time1 = Integer.valueOf(o1.split("\t")[0].substring(11).replaceAll(":", ""));
            Integer time2 = Integer.valueOf(o2.split("\t")[0].substring(11).replaceAll(":", ""));
            return time1 - time2;
        });
        BaseUtils.writeFile(optionFilePath, lines);

        return ivList;
    }

    public static List<AggregateOptionIV> getAggregateGreekList(String optionCode, String date) throws Exception {
        if (dateToGreekListMap.containsKey(date) && dateToGreekListMap.get(date).containsKey(optionCode)) {
            return dateToGreekListMap.get(date).get(optionCode);
        }

        List<Double> aggregateIvList = getAggregateIvList(optionCode, date);
        if (CollectionUtils.isEmpty(aggregateIvList)) {
            return Lists.newArrayListWithExpectedSize(0);
        }

        return dateToGreekListMap.get(date).get(optionCode);
    }

    public static void main(String[] args) throws Exception {
        //        getAggregateIv("BB220408P00006500", "2022-04-01");
        //        getAggregateIv("BB220408P00006500", "2022-04-01");
        //        getAggregateIv("SOFI230623C00009500", "2023-06-16");
        init();
        System.out.println();
    }
}
