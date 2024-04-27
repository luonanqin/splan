package luonq.polygon;

import bean.StockKLine;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;
import util.BaseUtils;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static util.Constants.*;

/**
 * Created by Luonanqin on 2023/5/5.
 */
public class GetSplitHistoricalDaily {


    public static void main(String[] args) throws Exception {
        System.getProperties().setProperty("webdriver.chrome.driver", "chromedriver");

        BlockingQueue<ChromeDriver> driverQueue = new LinkedBlockingQueue<>();
        int threadCount = 2;
        for (int i = 0; i < threadCount; i++) {
            ChromeOptions chromeOptions = new ChromeOptions();
            chromeOptions.addArguments("--remote-allow-origins=*");
            ChromeDriver driver = new ChromeDriver(chromeOptions);
            driver.manage().window().setSize(new Dimension(1280, 1027));
            driver.manage().timeouts().pageLoadTimeout(10, TimeUnit.SECONDS);
            BaseUtils.loginBarchart(driver);
            driverQueue.offer(driver);
        }

        // 从昨天开始每365天查询一次并抓取
        // 默认step=2，每次从最左边开始抓取时，如果获取不到field-value，则表示改股票所有数据均已抓去完成，开始下一个股票
        List<String> stockList = Lists.newArrayList("MRBK", "GCBC", "MNST", "CHDN");

        String testDay = "01/03/2023";
        String initDay = "01/03/2000";
        LocalDate initDayParse = LocalDate.parse(initDay, FORMATTER);
        int corePoolSize = threadCount;
        int maximumPoolSize = corePoolSize;
        long keepAliveTime = 60L;
        LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();
        Executor cachedThread = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.MILLISECONDS, workQueue);
        for (String stock : stockList) {
//            List<StockKLine> testData = GetHistoricalDaily.getHistoricalDaily(stock, Lists.newArrayList(testDay), new HttpClient());
//            StockKLine firstKLine = BaseUtils.getFirstKLine(Constants.HIS_BASE_PATH + "2023daily/" + stock);
//            if (testData.get(0).getOpen() == firstKLine.getOpen()) {
//                System.out.println("has grab split data: " + stock);
//                continue;
//            }

            ChromeDriver driver = driverQueue.take();
            cachedThread.execute(() -> asyncProcess(driverQueue, driver, stock));
        }
    }

    public static void asyncProcess(BlockingQueue<ChromeDriver> driverQueue, ChromeDriver driver, String stock) {
        try {
            WebElement canvas = loadStockCanvas(driver, stock);

            // 断点续抓需要获取已经抓过的最新日期
            String latestDay = getLatestDay(stock);
            String endDate = StringUtils.defaultString(latestDay, "01/01/2023");
            int year = Integer.parseInt(endDate.substring(endDate.lastIndexOf("/") + 1));
            endDate = "01/01/" + year;
            while (true) {
                String beginDate = "01/01/" + (--year);

                setDateRange(driver, beginDate, endDate);
                System.out.println("confirm new date range: " + stock + " " + beginDate + " - " + endDate);
                List<StockKLine> dataList = getDataFromCanvas(driver, canvas);

                appendTradeDay(stock, Lists.reverse(dataList));
                System.out.println("write finish: " + stock + " " + beginDate + " - " + endDate);

                //                List<Integer> dataList = Lists.newArrayList(1);
                if (CollectionUtils.isEmpty(dataList)) {
                    renameFile(stock);
                    break;
                } else {
                    endDate = beginDate;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(e.getMessage());
        } finally {
            driverQueue.offer(driver);
        }
    }

    public static Set<String> hasGrab() {
        File file = new File(GRAB_PATH);
        Set<String> hasGrab = Sets.newHashSet();
        for (String fileName : file.list()) {
            if (!fileName.endsWith("_day")) {
                continue;
            }
            hasGrab.add(fileName.substring(0, fileName.indexOf("_")));
        }
        return hasGrab;
    }

    public static void renameFile(String stock) {
        String fileName = GRAB_PATH + stock;
        File file = new File(fileName);
        if (file.exists()) {
            file.renameTo(new File(fileName + "_day"));
            System.out.println("rename " + stock + " finish");
            return;
        }
        System.out.println("rename " + stock + " is not exist");
    }

    public static String getLatestDay(String stock) throws Exception {
        File file = new File(GRAB_PATH + stock);
        if (!file.exists()) {
            file.createNewFile();
            return null;
        } else {
            BufferedReader br = new BufferedReader(new FileReader(file));
            String latest = null, str;
            while (StringUtils.isNotBlank(str = br.readLine())) {
                latest = str.split(",")[0];
            }
            return latest;
        }
    }

    public static String getDownloadLatestDay(String stock) throws Exception {
        File file = new File(DAILY_PATH);
        for (File stockFile : file.listFiles()) {
            if (!stockFile.getName().startsWith(stock.toLowerCase() + "_")) {
                continue;
            }
            BufferedReader br = new BufferedReader(new FileReader(stockFile));
            String latest = null, str;
            while (StringUtils.isNotBlank(str = br.readLine())) {
                if (str.startsWith("\"")) {
                    continue;
                }
                latest = str;
            }
            return latest.split(",")[0];
        }
        return null;
    }

    public static void appendTradeDay(String stock, List<StockKLine> dataList) throws Exception {
        File file = new File(GRAB_PATH + stock);

        FileOutputStream fos = new FileOutputStream(file, true);
        OutputStreamWriter osw = new OutputStreamWriter(fos, "utf-8");

        Set<String> daySet = Sets.newHashSet();
        for (StockKLine stockKLine : dataList) {
            String day = stockKLine.getDate();
            // 为空或者包含则跳过不写入
            if (StringUtils.isBlank(day) || daySet.contains(day)) {
                continue;
            }
            // 写入内容
            osw.write(stockKLine.toString());
            // 换行
            osw.write("\n");
            daySet.add(day);
        }
        osw.flush();
        osw.close();
        fos.close();
    }


    public static WebElement loadStockCanvas(ChromeDriver driver, String stock) throws Exception {
        BaseUtils.viewloadPage(driver, "https://www.barchart.com/stocks/quotes/" + stock + "/interactive-chart", By.className("chart-canvas-container"));
        return driver.findElement(By.className("chart-canvas-container"));
    }

    public static void setDateRange(ChromeDriver driver, String beginDate, String endDate) throws Exception {
        int retryTimes = 10;
        while (retryTimes > 0) {
            try {
                //                System.out.println("set new date range: " + beginDate + " - " + endDate);
                driver.findElement(By.xpath("//div[@data-dropdown-id='bc-interactive-chart-dropdown-period']//i[@class='bc-glyph-chevron-down bc-dropdown-flexible-arrow']")).click();
                driver.findElement(By.xpath("//li[@data-ng-click='changePeriod(item.period)' and contains(text(),'Date Range')]")).click();
                driver.findElement(By.xpath("//div[@class='show-for-medium-up for-tablet-and-desktop ng-scope']//input[@data-ng-model='selectedAggregation.range.from']")).sendKeys(Keys.chord(Keys.COMMAND, "a"), Keys.DELETE, beginDate);
                driver.findElement(By.xpath("//div[@class='show-for-medium-up for-tablet-and-desktop ng-scope']//input[@data-ng-model='selectedAggregation.range.to']")).sendKeys(endDate);
                new Actions(driver).sendKeys(Keys.ENTER).perform();
                driver.findElement(By.xpath("//button[@data-ng-click='modalConfirm()']")).click();
                //                System.out.println("confirm new date range: " + beginDate + " - " + endDate);
                break;
            } catch (Exception e) {
                retryTimes--;
            }
        }
    }

    private static List<StockKLine> getDataFromCanvas(ChromeDriver driver, WebElement canvas) throws InterruptedException {
        Actions actions = new Actions(driver);
        actions.moveToElement(canvas, 0, 0).perform();
        return getMoveData(driver, actions, -520, 1);
    }

    private static List<StockKLine> getMoveData(ChromeDriver driver, Actions actions, int xOffset, int step) {
        actions.moveByOffset(xOffset, 0).perform();

        long begin = System.currentTimeMillis();
        String lastDate = null;
        int moveAdd = 0, moveMaxTimes = 15, moveTimes = 0;
        List<StockKLine> dataList = Lists.newArrayList();
        while (moveTimes < moveMaxTimes) {
            String date = null;
            try {
                date = driver.findElement(By.xpath("//span[@class='field-name']")).getText();
            } catch (Exception e) {
                if (StringUtils.isBlank(date)) {
                    actions.moveByOffset(step, 0).perform();
                    moveTimes++;
                    System.out.println("move to begin retry " + moveTimes);
                    continue;
                }
            }

            if (lastDate != null && StringUtils.equals(lastDate, date)) {
                try {
                    moveAdd += step;
                    actions.moveByOffset(step, 0).perform();
                    moveTimes++;
                } catch (Exception e) {
                    break;
                }
            } else {
                List<WebElement> elements = driver.findElements(By.xpath("//span[@class='field-value']"));
                String open = StringUtils.defaultIfBlank(elements.get(0).getText(), "0");
                String high = StringUtils.defaultIfBlank(elements.get(1).getText(), "0");
                String low = StringUtils.defaultIfBlank(elements.get(2).getText(), "0");
                String close = StringUtils.defaultIfBlank(elements.get(3).getText(), "0");
                String change = StringUtils.defaultIfBlank(elements.get(4).getText(), "0");
                String volume = StringUtils.defaultIfBlank(elements.get(5).getText(), "0");
                if (open.equals("0")) {
                    lastDate = date;
                    continue;
                }
                StockKLine kLine = StockKLine.builder()
                  .date(date)
                  .open(Double.valueOf(open.replace(",", "")))
                  .high(Double.valueOf(high.replace(",", "")))
                  .low(Double.valueOf(low.replace(",", "")))
                  .close(Double.valueOf(close.replace(",", "")))
                  .change(Double.valueOf(change.replace(",", "")))
                  .volume(BigDecimal.valueOf(Double.valueOf(volume.replace(",", ""))))
                  .build();
                dataList.add(kLine);
                //                System.out.println(kLine);

                lastDate = date;
                moveTimes = 0;
            }
            if (dataList.size() > 1) {
                step = 2;
            }
            if (dataList.size() > 250) {
                step = 1;
            }
        }
        System.out.println("cost " + ((System.currentTimeMillis() - begin) / 1000) + "s");
        return dataList;
    }
}
