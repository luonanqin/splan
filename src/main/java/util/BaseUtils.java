package util;

import barchart.DownloadStockHistory;
import bean.BOLL;
import bean.EarningDate;
import bean.FrontReinstatement;
import bean.SplitStockInfo;
import bean.StockKLine;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import javax.mail.Authenticator;
import javax.mail.Message;
import javax.mail.PasswordAuthentication;
import javax.mail.Session;
import javax.mail.internet.InternetAddress;
import javax.mail.internet.MimeMessage;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import static util.Constants.GRAB_PATH;
import static util.Constants.HIS_BASE_PATH;

/**
 * Created by Luonanqin on 2023/2/5.
 */
@Slf4j
public class BaseUtils {

    public static void viewloadPage(ChromeDriver driver, String url, By checkBy) {
        while (true) {
            try {
                driver.get(url);
                //                driver.findElement(checkBy);
                new WebDriverWait(driver, Duration.ofSeconds(5)).until(d -> driver.findElement(checkBy));
                return;
            } catch (Exception e) {
                try {
                    driver.findElement(checkBy);
                    return;
                } catch (Exception ex) {
                    log.info("retry load " + url);
                }
            }
        }
    }

    public static void clickLoadPage(ChromeDriver driver, By clickBy, By checkBy) {
        try {
            driver.findElement(clickBy).click();
        } catch (Exception e) {
            while (true) {
                try {
                    driver.findElement(checkBy);
                    return;
                } catch (Exception exception) {
                    refresh(driver);
                }
            }
        }
    }

    public static void refresh(ChromeDriver driver) {
        try {
            driver.navigate().refresh();
        } finally {
            log.info("refresh current page!");
            return;
        }
    }

    public static void loginBarchart(ChromeDriver driver) throws InterruptedException {
        log.info("loading login page");
        viewloadPage(driver, "https://www.barchart.com/login", By.xpath("//h1[@class='sign-in-block_header']"));
        //        driver.get("https://www.barchart.com/login");
        log.info("finish loading");
        //        TimeUnit.SECONDS.sleep(5);

        // login
        log.info("input email and pwd");
        driver.findElement(By.cssSelector("[name='email']")).sendKeys("qinnanluo@sina.com");
        driver.findElement(By.cssSelector("[name='password']")).sendKeys("luonq134931");
        log.info("start login");
        //        driver.findElement(By.className("login-button")).click();
        clickLoadPage(driver, By.className("login-button"), By.tagName("span"));
        // 登录后需要休眠一会儿确定已经登录
        //        while (true) {
        //            String MyAccount;
        //            try {
        //                MyAccount = driver.findElement(By.tagName("span")).getText();
        //                if ("My Account".equals(MyAccount)) {
        //                    log.info("finish login");
        //                    break;
        //                }
        //            } catch (Exception e) {
        //                log.info("wait login");
        //                TimeUnit.SECONDS.sleep(1);
        //            }
        //        }
    }

    public static Map<String, String> getOpenData(String market) throws IOException {
        Map<String, String> openDate = Maps.newHashMap();
        BufferedReader openBr = new BufferedReader(new FileReader(HIS_BASE_PATH + "open/" + market + ".txt"));
        String open;
        while (StringUtils.isNotBlank(open = openBr.readLine())) {
            String[] split = open.split("\t");
            String code = split[0];
            String date = split[1];
            if (StringUtils.isBlank(date) || date.equalsIgnoreCase("null")) {
                continue;
            }

            openDate.put(code, date);
        }
        return openDate;
    }

    public static List<String> getHasOptionStockList(String market) throws IOException {
        List<String> stockList = Lists.newArrayList();
        BufferedReader br = new BufferedReader(new InputStreamReader(DownloadStockHistory.class.getResourceAsStream("/historicalData/code/hasOption/" + market)));
        String hasOption;
        while (StringUtils.isNotBlank(hasOption = br.readLine())) {
            stockList.add(hasOption);
        }
        br.close();

        return stockList;
    }

    public static List<String> getStockListOrderByOpenDesc(String market) throws IOException {
        return getStockListOrderByOpen(market, false);
    }

    public static List<String> getStockListOrderByOpenAsc(String market) throws IOException {
        return getStockListOrderByOpen(market, true);
    }

    public static List<String> getStockListOrderByOpen(String market, boolean asc) throws IOException {
        List<String> stockList = getHasOptionStockList(market);

        Map<String, String> openDate = getOpenData(market);

        stockList = stockList.stream().filter(s -> openDate.get(s) != null).sorted((o1, o2) -> {
            String date1 = openDate.get(o1);
            String date2 = openDate.get(o2);
            LocalDate localDate1 = LocalDate.parse(date1);
            LocalDate localDate2 = LocalDate.parse(date2);
            if (asc) {
                return localDate1.isBefore(localDate2) ? -1 : 1;
            } else {
                return !localDate1.isBefore(localDate2) ? -1 : 1;
            }
        }).collect(Collectors.toList());

        return stockList;
    }

    public static void asyncWriteStockKLine(String filePath, List<StockKLine> list) throws Exception {
        new Thread(() -> {
            BufferedWriter bw = null;
            try {
                bw = new BufferedWriter(new FileWriter(filePath));
                for (StockKLine l : list) {
                    bw.write(l.toString());
                    bw.write("\n");
                }
                bw.close();
            } catch (IOException e) {
                if (bw != null) {
                    try {
                        bw.close();
                    } catch (IOException ioException) {
                        ioException.printStackTrace();
                    }
                }
            }
        }).start();
    }

    public static void writeStockKLine(String filePath, List<StockKLine> list) throws Exception {
        BufferedWriter bw = new BufferedWriter(new FileWriter(filePath));
        for (StockKLine l : list) {
            bw.write(l.toString());
            bw.write("\n");
        }
        bw.close();
    }

    public static Map<String, String> originStockFileMap(String period) throws Exception {
        Map<String, String> stockFileMap = Maps.newHashMap();
        File dir = new File(HIS_BASE_PATH + period + "/");
        for (File file : dir.listFiles()) {
            String fileName = file.getName();
            if (!fileName.endsWith(".csv")) {
                continue;
            }
            String stock = fileName.substring(0, fileName.indexOf("_" + period));
            stockFileMap.put(stock.toUpperCase(), file.getAbsolutePath());
        }

        return stockFileMap;
    }

    public static Map<String, String> grabStockFileMap() throws Exception {
        Map<String, String> stockFileMap = Maps.newHashMap();
        File dir = new File(GRAB_PATH);
        for (File file : dir.listFiles()) {
            String fileName = file.getName();
            if (!fileName.endsWith("_day")) {
                continue;
            }
            String stock = fileName.substring(0, fileName.indexOf("_day"));
            stockFileMap.put(stock.toUpperCase(), file.getAbsolutePath());
        }

        return stockFileMap;
    }

    public static Map<String, String> getFileMap(String dirPath) throws Exception {
        Map<String, String> fileMap = Maps.newHashMap();
        File dir = new File(dirPath);
        for (File file : dir.listFiles()) {
            String fileName = file.getName();
            if (fileName.endsWith(".DS_Store")) {
                continue;
            }
            fileMap.put(fileName.toUpperCase(), file.getAbsolutePath());
        }

        return fileMap;
    }

    public static List<StockKLine> loadDataToKline(String filePath) throws Exception {
        return loadDataToKline(filePath, null, null);
    }

    public static List<StockKLine> loadDataToKline(String filePath, Integer beforeYear) throws Exception {
        return loadDataToKline(filePath, beforeYear, null);
    }

    public static List<StockKLine> loadDataToKline(String filePath, Integer beforeYear, Integer afterYear) throws Exception {
        BufferedReader br;
        try {
            br = new BufferedReader(new FileReader(filePath));
        } catch (FileNotFoundException e) {
            return Lists.newArrayListWithExpectedSize(0);
        }

        if (filePath.contains("_historical")) {
            br.readLine();
        }

        List<String> list = Lists.newLinkedList();
        String line;
        while (StringUtils.isNotBlank(line = br.readLine())) {
            list.add(line);
        }
        br.close();

        return convertToKLine(list, beforeYear, afterYear);
    }

    public static List<StockKLine> convertToKLine(List<String> lineList) {
        return convertToKLine(lineList, 2022, null);
    }

    public static List<StockKLine> convertToKLine(List<String> lineList, Integer beforeYear, Integer afterYear) {
        if (beforeYear == null) {
            beforeYear = 2022;
        }
        if (afterYear == null) {
            afterYear = 1900;
        }
        List<StockKLine> list = Lists.newLinkedList();
        for (String line : lineList) {
            // 修正数字中带逗号
            if (line.contains("\"")) {
                StringBuilder sb = new StringBuilder();
                boolean open = false;
                for (int i = 0; i < line.length(); i++) {
                    char c = line.charAt(i);
                    if (c == '\"') {
                        if (!open) {
                            open = true;
                        } else if (open) {
                            open = false;
                        }
                    } else {
                        if (open && c == ',') {
                            continue;
                        }
                        sb.append(c);
                    }
                }
                line = sb.toString();
            }
            String[] split = line.split(",");
            if (split.length < 7) {
                continue;
            }

            String date = split[0];
            String year = date.substring(date.lastIndexOf("/") + 1);
            Integer yearInt = Integer.valueOf(year);
            if (beforeYear < yearInt || afterYear >= yearInt) {
                continue;
            }
            double open = Double.valueOf(split[1]);
            double high = Double.valueOf(split[2]);
            double low = Double.valueOf(split[3]);
            double close = Double.valueOf(split[4]);
            double change = Double.valueOf(split[5]);
            String changePnt = "";
            double volume = 0;
            if (split.length >= 8) {
                changePnt = split[6];
                volume = Double.valueOf(split[7]);
            } else if (split.length == 7) {
                volume = Double.valueOf(split[6]);
            }

            StockKLine daily = new StockKLine();
            daily.setDate(date);
            daily.setOpen(open);
            daily.setClose(close);
            daily.setHigh(high);
            daily.setLow(low);
            daily.setChange(change);
            if (StringUtils.isNotBlank(changePnt)) {
                if (changePnt.contains("%")) {
                    daily.setChangePnt(Double.valueOf(changePnt.substring(0, changePnt.length() - 1)));
                } else {
                    daily.setChangePnt(Double.valueOf(changePnt));
                }
            }
            daily.setVolume(BigDecimal.valueOf(volume));

            list.add(daily);
        }
        return list;
    }

    public static List<BOLL> readBollFile(String filePath, Integer beforeYear) throws Exception {
        return readBollFile(filePath, beforeYear, null);
    }

    public static List<BOLL> readBollFile(String filePath, Integer beforeYear, Integer afterYear) throws Exception {
        if (StringUtils.isBlank(filePath)) {
            return Lists.newArrayListWithExpectedSize(0);
        }
        if (beforeYear == null) {
            beforeYear = 2022;
        }
        if (afterYear == null) {
            afterYear = 1900;
        }

        List<BOLL> lineList = Lists.newArrayList();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(filePath));
        } catch (FileNotFoundException e) {
            //            log.info("can not find file: " + filePath);
            return Lists.newArrayList();
        }

        String line;
        while (StringUtils.isNotBlank(line = br.readLine())) {
            BOLL convert = BOLL.convert(line);
            String date = convert.getDate();
            String year = date.substring(date.lastIndexOf("/") + 1);
            int yearInt = Integer.valueOf(year);
            if (afterYear >= yearInt) {
                break;
            }
            if (beforeYear < yearInt) {
                continue;
            }
            lineList.add(convert);
        }
        br.close();

        return lineList;
    }

    public static List<String> readMaFile(String filePath, Integer beforeYear, Integer afterYear) throws Exception {
        if (beforeYear == null) {
            beforeYear = 2022;
        }
        if (afterYear == null) {
            afterYear = 1900;
        }

        List<String> lineList = Lists.newArrayList();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(filePath));
        } catch (FileNotFoundException e) {
            //            log.info("can not find file: " + filePath);
            return Lists.newArrayList();
        }

        String line;
        while (StringUtils.isNotBlank(line = br.readLine())) {
            String[] split = line.split(",");
            String date = split[0];
            String year = date.substring(date.lastIndexOf("/") + 1);
            int yearInt = Integer.valueOf(year);
            if (afterYear >= yearInt) {
                break;
            }
            if (beforeYear < yearInt) {
                continue;
            }
            lineList.add(line);
        }
        br.close();

        return lineList;
    }

    public static List<String> readFile(String filePath) throws Exception {
        if (StringUtils.isBlank(filePath)) {
            return Lists.newArrayListWithExpectedSize(0);
        }

        List<String> lineList = Lists.newArrayList();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(filePath));
        } catch (FileNotFoundException e) {
            //            log.info("can not find file: " + filePath);
            return Lists.newArrayList();
        }

        String line;
        while (StringUtils.isNotBlank(line = br.readLine())) {
            lineList.add(line);
        }
        br.close();

        return lineList;
    }

    public static List<String> readFile(File file) throws Exception {
        List<String> lineList = Lists.newArrayList();
        BufferedReader br = null;
        try {
            br = new BufferedReader(new FileReader(file));
        } catch (FileNotFoundException e) {
            //            log.info("can not find file: " + file.getPath());
            return Lists.newArrayList();
        }

        String line;
        while (StringUtils.isNotBlank(line = br.readLine())) {
            lineList.add(line);
        }
        br.close();

        return lineList;
    }

    public static void appendIfFile(String filePath, List<String> list) throws Exception {
        FileWriter bw = new FileWriter(filePath, true);
        for (String l : list) {
            bw.write(l);
            bw.write("\n");
        }
        bw.close();
    }

    public static void writeFile(String filePath, List<String> list) throws Exception {
        BufferedWriter bw = new BufferedWriter(new FileWriter(filePath));
        for (String l : list) {
            bw.write(l);
            bw.write("\n");
        }
        bw.close();
    }

    public static Integer dateToInt(String date) {
        String year = date.substring(6);
        String newDate = year + date.substring(0, 5);
        return Integer.parseInt(newDate.replace("/", ""));
    }

    public static int formatDateToInt(String date) {
        return Integer.parseInt(date.replaceAll("\\-", ""));
    }

    public static int kLineDateToInt(StockKLine kLine) {
        return dateToInt(kLine.getDate());
    }

    private static LocalDate _2000 = LocalDate.parse("01/03/2000", Constants.FORMATTER);

    public static boolean after_2000(String dailyFile) throws Exception {
        List<StockKLine> stockKLines = BaseUtils.loadDataToKline(dailyFile);
        if (CollectionUtils.isEmpty(stockKLines)) {
            return false;
        }
        StockKLine earliest = stockKLines.get(stockKLines.size() - 1);
        String date = earliest.getDate();
        LocalDate parse = LocalDate.parse(date, Constants.FORMATTER);
        return parse.isAfter(_2000);
    }

    public static StockKLine getFirstKLine(String filePath) throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(filePath));
        if (filePath.contains("_historical")) {
            br.readLine();
        }

        String line;
        String first = "";
        while (StringUtils.isNotBlank(line = br.readLine())) {
            first = line;
        }
        br.close();

        return convertToKLine(Lists.newArrayList(first)).get(0);
    }

    public static StockKLine getLatestKLine(String filePath) throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(filePath));
        if (filePath.contains("_historical")) {
            br.readLine();
        }

        String first = br.readLine();
        br.close();
        if (StringUtils.isBlank(first)) {
            return null;
        }

        int year = LocalDate.now().getYear();
        return convertToKLine(Lists.newArrayList(first), year, 0).get(0);
    }

    public static String getLatestLine(String filePath) throws Exception {
        BufferedReader br = new BufferedReader(new FileReader(filePath));
        if (filePath.contains("_historical")) {
            br.readLine();
        }

        String first = br.readLine();
        br.close();
        if (StringUtils.isBlank(first)) {
            return null;
        }

        return first;
    }

    public static Set<String> getMergeStock() throws Exception {
        Set<String> merge = Sets.newHashSet();
        List<String> lines = readFile(Constants.SPLIT_PATH);
        for (String line : lines) {
            String[] split = line.split(",");
            String stock = split[1];
            String from = split[2];
            String to = split[3];
            int fromNum = Integer.valueOf(from);
            int toNum = Integer.valueOf(to);
            if (fromNum > toNum) {
                merge.add(stock);
            }
        }
        return merge;
    }

    public static Set<SplitStockInfo> getSplitStockInfo() throws Exception {
        Set<SplitStockInfo> splitSet = Sets.newHashSet();
        List<String> lines = readFile(Constants.SPLIT_PATH + "splitInfo");
        for (String line : lines) {
            String[] split = line.split(",");
            String date = split[0];
            String stock = split[1];
            String from = split[2];
            String to = split[3];
            int fromNum = Integer.valueOf(from);
            int toNum = Integer.valueOf(to);
            if (fromNum < toNum) {
                SplitStockInfo splitStockInfo = new SplitStockInfo();
                splitStockInfo.setDate(date);
                splitStockInfo.setStock(stock);
                splitStockInfo.setFrom(fromNum);
                splitStockInfo.setTo(toNum);
                splitSet.add(splitStockInfo);
            }
        }
        return splitSet;
    }

    public static Set<FrontReinstatement> getFrontReinstatementInfo() throws Exception {
        Set<FrontReinstatement> set = Sets.newHashSet();
        List<String> lines = readFile(Constants.SPLIT_PATH + "rehab");
        for (String line : lines) {
            String[] split = line.split(" ");
            String stock = split[0];
            String date = split[1];
            double factor = Double.valueOf(split[2]);
            int factorType = Integer.parseInt(split[3]);
            FrontReinstatement fr = new FrontReinstatement();
            fr.setStock(stock);
            fr.setDate(date);
            fr.setFactor(factor);
            fr.setFactorType(factorType);
            set.add(fr);
        }
        return set;
    }

    public static void filterStock(Set<String> set) throws Exception {
        // 过滤所有合股
        Set<String> mergeStock = BaseUtils.getMergeStock();
        set.removeAll(mergeStock);
        log.info(String.format("filter merge stock, the stock set size is %d", set.size()));

        // 过滤所有拆股
        Set<SplitStockInfo> splitStockInfo = BaseUtils.getSplitStockInfo();
        Set<String> splitStock = splitStockInfo.stream().map(SplitStockInfo::getStock).collect(Collectors.toSet());
        set.removeAll(splitStock);
        log.info(String.format("filter split stock, the stock set size is %d", set.size()));

        // 过滤所有今年前复权因子低于0.98的
        LocalDate firstDay = LocalDate.parse("2023-01-01", DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        Set<FrontReinstatement> reinstatementInfo = BaseUtils.getFrontReinstatementInfo();
        Map<String, FrontReinstatement> map = reinstatementInfo.stream().collect(Collectors.toMap(FrontReinstatement::getStock, Function.identity()));
        for (String stock : map.keySet()) {
            FrontReinstatement fr = map.get(stock);
            double factor = fr.getFactor();
            if (factor > 0.98) {
                //            if (factor > 0.98 && factor < 2) {
                continue;
            }

            String date = fr.getDate();
            LocalDate dateParse = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            if (dateParse.isAfter(firstDay)) {
                set.remove(stock);
            }
        }
        log.info(String.format("filter front reinstatement less 0.98 stock, the stock set size is %d", set.size()));
    }

    public static Map<String, List<EarningDate>> getEarningDate(String date) throws Exception {
        Map<String, List<EarningDate>> map = Maps.newHashMap();
        if (StringUtils.isNotBlank(date)) {
            String filePath = Constants.HIS_BASE_PATH + "earning/" + date;
            LocalDate dateParse = LocalDate.parse(date, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
            date = dateParse.format(Constants.FORMATTER);
            LocalDate nextLocalDate = dateParse.plusDays(1);
            if (nextLocalDate.getDayOfWeek().getValue() > 5) {
                nextLocalDate = nextLocalDate.plusDays(2);
            }
            String nextDate = nextLocalDate.format(Constants.FORMATTER);
            List<EarningDate> list = getEarningDateData(date, nextDate, filePath);

            for (EarningDate earningDate : list) {
                String actualDate = earningDate.getActualDate();
                if (!map.containsKey(actualDate)) {
                    map.put(actualDate, Lists.newArrayList());
                }
                map.get(actualDate).add(earningDate);
            }
        } else {
            Map<String, String> fileMap = getFileMap(Constants.HIS_BASE_PATH + "earning/");
            for (String fileName : fileMap.keySet()) {
                String filePath = fileMap.get(fileName);
                LocalDate dateParse = LocalDate.parse(fileName, DateTimeFormatter.ofPattern("yyyy-MM-dd"));
                date = dateParse.format(Constants.FORMATTER);
                LocalDate nextLocalDate = dateParse.plusDays(1);
                if (nextLocalDate.getDayOfWeek().getValue() > 5) {
                    nextLocalDate = nextLocalDate.plusDays(2);
                }
                String nextDate = nextLocalDate.format(Constants.FORMATTER);

                List<EarningDate> list = getEarningDateData(date, nextDate, filePath);
                for (EarningDate earningDate : list) {
                    String actualDate = earningDate.getActualDate();
                    if (!map.containsKey(actualDate)) {
                        map.put(actualDate, Lists.newArrayList());
                    }
                    map.get(actualDate).add(earningDate);
                }
            }
        }

        return map;
    }

    private static List<EarningDate> getEarningDateData(String date, String nextDate, String filePath) throws Exception {
        List<String> lines = readFile(filePath);
        List<EarningDate> list = Lists.newArrayList();
        Map<String, EarningDate> stockEarningMap = Maps.newHashMap();
        for (String line : lines) {
            int index = line.indexOf(" ");
            String stock = line.substring(0, index);
            String earningType = line.substring(index + 1);
            String actualDate;
            if (StringUtils.equals(EarningDate.AFTER_MARKET_CLOSE, earningType)) {
                actualDate = nextDate;
            } else if (StringUtils.equals(EarningDate.BEFORE_MARKET_OPEN, earningType)) {
                //            } else {
                actualDate = date;
            } else {
                continue;
            }

            EarningDate earning = new EarningDate(stock, date, earningType, actualDate);
            list.add(earning);
            //            // 如果之前不存在则直接put，如果之前存在且是TAS则直接put，否则跳过
            //            EarningDate existEarningDate = stockEarningMap.get(stock);
            //            if (existEarningDate == null) {
            //                stockEarningMap.put(stock, earning);
            //            } else {
            //                String existEarningType = existEarningDate.getEarningType();
            //                if (StringUtils.equals(EarningDate.TAS, existEarningType)) {
            //                    stockEarningMap.put(stock, earning);
            //                }
            //            }
        }

        //        list.addAll(stockEarningMap.values());
        return list;
    }

    public static LocalDateTime getSummerTime(Integer year) {
        if (year == null) {
            year = LocalDate.now().getYear();
        }

        LocalDateTime summerTime = LocalDateTime.of(year, 3, 1, 0, 0, 0);
        int sundayTimes = 0;
        while (true) {
            if (summerTime.getDayOfWeek().getValue() == 7) {
                sundayTimes++;
            }
            if (sundayTimes == 2) {
                break;
            }
            summerTime = summerTime.plusDays(1);
        }
        return summerTime;
    }

    public static LocalDateTime getWinterTime(Integer year) {
        if (year == null) {
            year = LocalDate.now().getYear();
        }

        LocalDateTime winterTime = LocalDateTime.of(year, 11, 1, 0, 0, 0);
        while (true) {
            if (winterTime.getDayOfWeek().getValue() == 7) {
                break;
            }
            winterTime = winterTime.plusDays(1);
        }
        return winterTime;
    }

    public static LocalDate getFirstWorkDay() {
        LocalDate firstDay = LocalDate.now().withMonth(1).withDayOfMonth(1);
        LocalDate firstWorkDay;
        while (true) {
            int dayOfWeek = firstDay.getDayOfWeek().getValue();
            if (dayOfWeek >= 1 && dayOfWeek <= 5) {
                firstWorkDay = firstDay;
                break;
            } else {
                firstDay = firstDay.plusDays(1);
            }
        }
        firstWorkDay = firstWorkDay.withDayOfMonth(2);
        return firstWorkDay;
    }

    public static void sendEmail(String subject, String message) {
        String userName = "1321271684@qq.com";
        String password = "blxcxmcerhxbhbfc";
        Properties properties = new Properties();

        properties.put("mail.transport.protocol", "smtp");
        properties.put("mail.smtp.auth", "true");
        //        properties.put("mail.smtp.starttls.enable", "true");
        properties.put("mail.smtp.host", "smtp.qq.com");
        properties.put("mail.smtp.port", 25);

        Session session = Session.getInstance(properties, new Authenticator() {
            @Override
            protected PasswordAuthentication getPasswordAuthentication() {
                return new PasswordAuthentication(userName, password);
            }
        });

        MimeMessage mimeMessage = new MimeMessage(session);

        try {
            mimeMessage.addFrom(new InternetAddress[] { new InternetAddress(userName) });
            mimeMessage.addRecipient(Message.RecipientType.TO, new InternetAddress("qinnanluo@sina.com"));
            mimeMessage.setSubject(subject);
            mimeMessage.setText(message);

            //            Transport.send(mimeMessage);
        } catch (Exception e) {
            log.error("sendEmail error. subject={}, message={}", subject, message, e);
        }
    }

    /**
     * MM/dd/yyyy -> yyyy-MM-dd
     */
    public static String formatDate(String date) {
        LocalDate oDate = LocalDate.parse(date, Constants.FORMATTER);
        return oDate.format(Constants.DB_DATE_FORMATTER);
    }

    /**
     * yyyy-MM-dd -> MM/dd/yyyy
     */
    public static String unformatDate(String date) {
        LocalDate oDate = LocalDate.parse(date, Constants.DB_DATE_FORMATTER);
        return oDate.format(Constants.FORMATTER);
    }

    public static void createDirectory(String path) {
        File dir = new File(path);
        if (dir.exists()) {
            return;
        }

        dir.mkdirs();
    }

    public static Map<String, Boolean> getMarginInfoMap() {
        BufferedReader br = null;
        Map<String, Boolean> map = Maps.newHashMap();
        try {
            br = new BufferedReader(new FileReader(Constants.USER_PATH + "marginData/marginInfo"));
            String line;
            while (StringUtils.isNotBlank(line = br.readLine())) {
                String[] split = line.split("\t");
                map.put(split[0], Boolean.valueOf(split[1]));
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return map;
    }

    public static Set<String> getOptionStock() {
        BufferedReader br = null;
        Set<String> result = Sets.newHashSet();
        try {
            br = new BufferedReader(new FileReader(Constants.USER_PATH + "optionData/option"));
            String line;
            while (StringUtils.isNotBlank(line = br.readLine())) {
                result.add(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return result;
    }

    public static Set<String> getWeekOptionStock() {
        BufferedReader br = null;
        Set<String> result = Sets.newHashSet();
        try {
            br = new BufferedReader(new FileReader(Constants.USER_PATH + "optionData/weekOption"));
            String line;
            while (StringUtils.isNotBlank(line = br.readLine())) {
                result.add(line);
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                br.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }

        return result;
    }

    public static String getOptionPutCode(String optionCallCode) {
        int c_index = optionCallCode.lastIndexOf("C");
        StringBuffer sb = new StringBuffer(optionCallCode);
        return sb.replace(c_index, c_index + 1, "P").toString();
    }

    public static void main(String[] args) throws Exception {
        Map<String, List<EarningDate>> map = getEarningDate("2023-11-16");
        System.out.println(map);
    }
}
