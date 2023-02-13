package util;

import barchart.DownloadStockHistory;
import bean.StockKLine;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.support.ui.WebDriverWait;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static util.Constants.BASE_PATH;

/**
 * Created by Luonanqin on 2023/2/5.
 */
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
                    System.out.println("retry load " + url);
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
            System.out.println("refresh current page!");
            return;
        }
    }

    public static void loginBarchart(ChromeDriver driver) throws InterruptedException {
        System.out.println("loading login page");
        viewloadPage(driver, "https://www.barchart.com/login", By.xpath("//h1[@class='sign-in-block_header']"));
        //        driver.get("https://www.barchart.com/login");
        System.out.println("finish loading");
//        TimeUnit.SECONDS.sleep(5);

        // login
        System.out.println("input email and pwd");
        driver.findElement(By.cssSelector("[name='email']")).sendKeys("qinnanluo@sina.com");
        driver.findElement(By.cssSelector("[name='password']")).sendKeys("luonq134931");
        System.out.println("start login");
        //        driver.findElement(By.className("login-button")).click();
        clickLoadPage(driver, By.className("login-button"), By.tagName("span"));
        // 登录后需要休眠一会儿确定已经登录
//        while (true) {
//            String MyAccount;
//            try {
//                MyAccount = driver.findElement(By.tagName("span")).getText();
//                if ("My Account".equals(MyAccount)) {
//                    System.out.println("finish login");
//                    break;
//                }
//            } catch (Exception e) {
//                System.out.println("wait login");
//                TimeUnit.SECONDS.sleep(1);
//            }
//        }
    }

    public static Map<String, String> getOpenData(String market) throws IOException {
        Map<String, String> openDate = Maps.newHashMap();
        BufferedReader openBr = new BufferedReader(new FileReader("src/main/resources/historicalData/open/" + market + ".txt"));
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

    public static void writeStockKLine(String filePath, List<StockKLine> list) throws Exception{
        BufferedWriter bw = new BufferedWriter(new FileWriter(filePath));
        for (StockKLine l : list) {
            bw.write(l.toString());
            bw.write("\n");
        }
        bw.close();
    }

    public static Map<String, String> originStockFileMap(String period) throws Exception {
        Map<String, String> stockFileMap = Maps.newHashMap();
        File dir = new File(BASE_PATH + period + "/");
        for (File file : dir.listFiles()) {
            String fileName = file.getName();
            if (!fileName.endsWith(".csv")) {
                continue;
            }
            String stock = fileName.substring(0, fileName.indexOf("_" + period));
            stockFileMap.put(stock, file.getAbsolutePath());
        }

        return stockFileMap;
    }

    public static List<StockKLine> loadOriginalData(String filePath) throws Exception {
        List<StockKLine> list = Lists.newArrayList();

        BufferedReader br = new BufferedReader(new FileReader(filePath));
        br.readLine();

        String line;
        while (StringUtils.isNotBlank(line = br.readLine())) {
            if (line.contains("\"")) {
                
            }
            String[] split = line.split(",");
            if (split.length < 8) {
                continue;
            }

            String date = split[0];
            double open = Double.valueOf(split[1]);
            double high = Double.valueOf(split[2]);
            double low = Double.valueOf(split[3]);
            double close = Double.valueOf(split[4]);
            double change = Double.valueOf(split[5]);
            String changePnt = split[6];
            double volume = Double.valueOf(split[7]);

            StockKLine daily = new StockKLine();
            daily.setDate(date);
            daily.setOpen(open);
            daily.setClose(close);
            daily.setHigh(high);
            daily.setLow(low);
            daily.setChange(change);
            daily.setChangePnt(Double.valueOf(changePnt.substring(0, changePnt.length() - 1)));
            daily.setVolume(BigDecimal.valueOf(volume));

            list.add(daily);
        }
        br.close();

        return list;
    }

}
