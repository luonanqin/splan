package barchart;

import org.apache.commons.lang3.ArrayUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import stock.FilterStock;
import util.BaseUtils;

import java.io.File;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

public class DownloadStockHistory {

    public static void main(String[] args) throws Exception {

        System.getProperties().setProperty("webdriver.chrome.driver", "chromedriver");
        ChromeOptions chromeOptions = new ChromeOptions();
        ChromeDriver driver = new ChromeDriver(chromeOptions);

        // login
        BaseUtils.loginBarchart(driver);

        // has option stock
        String market = "XNYS";
        List<String> stockList = BaseUtils.getStockListOrderByOpenAsc(market);

        /** download historical stock data */

        // for daily
        downloadHistoricalStock(driver, stockList, "daily", 100);

        // for weekly
        //                downloadHistoricalStock(driver, stockList, "weekly", 100);

        // for monthly
        //        downloadHistoricalStock(driver, stockList, "monthly", 30);

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

        List<String> flatTradeStockList = FilterStock.tradeFlat();
        File downloadDir = new File("/Users/luonanqin/Downloads"); // 公司和家里通用
        for (String stock : stockList) {
            if (flatTradeStockList.contains(stock)) {
                System.out.println("flat trade: " + stock);
                continue;
            }
            if (hasDownload.contains(stock.toLowerCase())) {
                System.out.println("has downloaded: " + stock);
                continue;
            }
//            driver.get("https://www.barchart.com/my/price-history/download/" + stock);
            BaseUtils.viewloadPage(driver, "https://www.barchart.com/stocks/quotes/" + stock + "/historical-download", By.xpath("//h1/span[text()='(" + stock + ")']"));
            driver.findElement(By.xpath("//select[@data-ng-model='frequency']")).click();
            driver.findElement(By.xpath("//option[@value='string:" + frequency + "']")).click();
            driver.findElement(By.xpath("//a[@data-historical='historical']")).click();

            if (count == 0 || !downloadStockData(downloadDir, stock)) {
                return;
            }
            count--;
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
            // 点击下载后，需要等一会儿确定文件已下载再跳转到下一个代码
            System.out.println("downloading " + stock + " " + retryTimes);
            TimeUnit.SECONDS.sleep(1);

            // 最多重试20次，超过则表示已达每日下载次数上限
            if (retryTimes > 20) {
                return false;
            }
            retryTimes++;
        }
        return true;
    }
}
