package barchart;

import org.apache.commons.lang3.ArrayUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import stock.FilterStock;
import util.BaseUtils;
import util.Constants;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class DownloadStockHistory {

    public static void main(String[] args) throws Exception {

        System.getProperties().setProperty("webdriver.chrome.driver", "chromedriver");
        ChromeOptions chromeOptions = new ChromeOptions();
        chromeOptions.addArguments("--remote-allow-origins=*");
        ChromeDriver driver = new ChromeDriver(chromeOptions);
//                ChromeDriver driver = null;

        // login
        BaseUtils.loginBarchart(driver);

        // has option stock
        String market = "XNYS";
        List<String> stockList = BaseUtils.getStockListOrderByOpenAsc(market);

        /** download historical stock data */

        // for daily
                        downloadHistoricalStock(driver, stockList, "daily", 100);

        // for weekly
//        downloadHistoricalStock(driver, stockList, "weekly", 100);

        // for monthly
//        downloadHistoricalStock(driver, stockList, "monthly", 100);

        // for quarterly
        //        downloadHistoricalStock(driver, stockList, "quarterly", 30);

        driver.quit();
    }

    private static void downloadHistoricalStock(ChromeDriver driver, List<String> stockList, String frequency, int count) throws Exception {
        String hasDownloadPath = "src/main/resources/historicalData/" + frequency;
        File hasDownloadFile = new File(hasDownloadPath);
        if (!hasDownloadFile.exists()) {
            System.out.println("please check the download path");
            return;
        }
        String[] existList = hasDownloadFile.list();
        String searchKey = "_" + frequency + "_historical-data";
        Set<String> hasDownload = Arrays.stream(existList).filter(s -> s.contains(searchKey)).map(s -> s.substring(0, s.indexOf(searchKey))).collect(Collectors.toSet());

        Map<String, String> dailyFileMap = BaseUtils.originStockFileMap("daily");

        List<String> flatTradeStockList = FilterStock.tradeFlat(Constants.DAILY_PATH);
        flatTradeStockList.addAll(FilterStock.tradeFlat(Constants.GRAB_ONE_YEAR_PATH));
        File downloadDir = new File("/Users/luonanqin/Downloads"); // ?????????????????????
        for (String stock : stockList) {
            if (flatTradeStockList.contains(stock)) {
                //                System.out.println("flat trade: " + stock);
//                continue;
            }
            //            if (hasDaily.contains(stock)) {
            //                System.out.println("has been download daily: " + stock);
            //                continue;
            //            }
            if (hasDownload.contains(stock.toLowerCase())) {
                //                System.out.println("has downloaded: " + stock);
                continue;
            }
//            if (BaseUtils.after_2000(dailyFileMap.get(stock))) {
                //                System.out.println("after 2000 year: " + stock);
//                continue;
//            }
            BaseUtils.viewloadPage(driver, "https://www.barchart.com/stocks/quotes/" + stock + "/historical-download", By.xpath("//div/span[text()='(" + stock + ")']"));
            driver.findElement(By.xpath("//select[@data-ng-model='frequency']")).click();
            driver.findElement(By.xpath("//option[@value='string:" + frequency + "']")).click();
            driver.findElement(By.xpath("//a[@data-historical='historical']")).click();

            if (count == 0 || !downloadStockData(downloadDir, stock)) {
                return;
            }
            count--;
            System.out.println(stock);
        }
    }

    private static boolean downloadStockData(File downloadDir, String stock) throws InterruptedException {
        int retryTimes = 1;
        while (true) {
            String[] stockFile = downloadDir.list((dir, name) -> name.startsWith(stock.toLowerCase() + "_"));
            if (ArrayUtils.isNotEmpty(stockFile)) {
                System.out.println("finish downloading " + stock);
                break;
            }
            // ????????????????????????????????????????????????????????????????????????????????????
            System.out.println("downloading " + stock + " " + retryTimes);
            TimeUnit.SECONDS.sleep(1);

            // ????????????20???????????????????????????????????????????????????
            if (retryTimes > 20) {
                return false;
            }
            retryTimes++;
        }
        return true;
    }
}
