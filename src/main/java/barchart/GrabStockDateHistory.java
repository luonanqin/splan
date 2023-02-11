package barchart;

import bean.StockKLine;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.common.collect.Sets;
import lombok.Data;
import lombok.ToString;
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
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

import static util.Constants.*;

/**
 * Created by Luonanqin on 2023/2/5.
 */
public class GrabStockDateHistory {

    public static void main(String[] args) throws Exception {
        String market = "XNAS";
        //        System.getProperties().setProperty("webdriver.chrome.driver", "chromedriver");
        ChromeOptions chromeOptions = new ChromeOptions();
        //        ChromeDriver driver = new ChromeDriver(chromeOptions);
        //        driver.manage().window().setSize(new Dimension(1280, 1027));
        //        driver.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(10));
        //        driver.switchTo().newWindow(WindowType.TAB);

        //        BaseUtils.loginBarchart(driver);
        //        ChromeDriver driver = null;

        // 从昨天开始每365天查询一次并抓取
        // 默认step=3，每次从最左边开始抓取时，如果获取不到field-value，则表示改股票所有数据均已抓去完成，开始下一个股票
        List<String> stockList = BaseUtils.getStockListOrderByOpenAsc(market);
        //        stockList.remove("STR");
        //        stockList.clear();
        //        stockList.add("PEP");

        // 已经抓取过的
        Set<String> hasGrab = hasGrab();

        String initDay = "01/03/2000";
        LocalDate initDayParse = LocalDate.parse(initDay, FORMATTER);
        int corePoolSize = 2;
        int maximumPoolSize = corePoolSize;
        long keepAliveTime = 60L;
        LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();
        Executor cachedThread = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.MILLISECONDS, workQueue);
        AtomicInteger thread = new AtomicInteger(corePoolSize);
        Lock lock = new ReentrantLock();
        Condition cond = lock.newCondition();
        for (String stock : stockList) {
            if (hasGrab.contains(stock)) {
                System.out.println("has grab: " + stock);
                continue;
            }

            String downloadLatestDay = getDownloadLatestDay(stock);
            if (StringUtils.isBlank(downloadLatestDay)) {
                System.out.println("has not download: " + stock);
                continue;
            }
            // 如果已经下载的daily最新日期大于01/01/2000，则不需要抓取这个日期之前的时间
            LocalDate downloadLDParse = LocalDate.parse(downloadLatestDay, FORMATTER);
            if (downloadLDParse.isAfter(initDayParse)) {
                continue;
            }
            try {
                while (true) {
                    lock.lock();
                    if (thread.get() < 0) {
                        cond.await();
                        System.out.println("begin grab new stock: " + stock);
                    } else {
                        thread.addAndGet(-1);
                        break;
                    }
                }
            } finally {
                cond.signalAll();
                lock.unlock();
            }
            cachedThread.execute(() -> {
                try {
                    ChromeDriver driverx = new ChromeDriver(chromeOptions);
                    driverx.manage().window().setSize(new Dimension(1280, 1027));
                    driverx.manage().timeouts().pageLoadTimeout(Duration.ofSeconds(10));
                    BaseUtils.loginBarchart(driverx);

                    //                    driverx.switchTo().newWindow(WindowType.WINDOW);
                    WebElement canvas = loadStockCanvas(driverx, stock);

                    // 断点续抓需要获取已经抓过的最新日期
                    String latestDay = getLatestDay(stock);
                    String endDate = StringUtils.defaultString(latestDay, "01/01/2000");
                    int year = Integer.parseInt(endDate.substring(endDate.lastIndexOf("/") + 1));
                    endDate = "01/01/" + year;
                    while (true) {
                        String beginDate = "01/01/" + (--year);

                        setDateRange(driverx, beginDate, endDate);
                        System.out.println("confirm new date range: " + stock + " " + beginDate + " - " + endDate);
                        List<StockKLine> dataList = getDataFromCanvas(driverx, canvas);

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
                    driverx.quit();
                    lock.lock();
                    thread.getAndIncrement();
                } catch (Exception e) {
                    e.printStackTrace();
                    System.out.println("grab " + stock + " error " + e.getMessage());
                } finally {
                    cond.signalAll();
                    lock.unlock();
                }

                //                System.out.println(thread.get());
                //                try {
                //                    TimeUnit.SECONDS.sleep(2);
                //                    lock.lock();
                //                    thread.getAndIncrement();
                //                } catch (InterruptedException e) {
                //                    e.printStackTrace();
                //                } finally {
                //                    cond.signalAll();
                //                    lock.unlock();
                //                }
            });

            //            WebElement canvas = loadStockCanvas(driver, stock);
            //            //                        WebElement canvas = null;
            //
            //            // 断点续抓需要获取已经抓过的最新日期
            //            String latestDay = getLatestDay(stock);
            //            String endDate = StringUtils.defaultString(latestDay, "01/01/2000");
            //            int year = Integer.parseInt(endDate.substring(endDate.lastIndexOf("/") + 1));
            //            endDate = "01/01/" + year;
            //            while (true) {
            //                String beginDate = "01/01/" + (--year);
            //
            //                setDateRange(driver, beginDate, endDate);
            //                List<StockKLine> dataList = getDataFromCanvas(driver, canvas);
            //
            //                appendTradeDay(stock, Lists.reverse(dataList));
            //                System.out.println("write finish: " + beginDate + " - " + endDate);
            //
            //                //                List<Integer> dataList = Lists.newArrayList(1);
            //                if (CollectionUtils.isEmpty(dataList)) {
            //                    renameFile(stock);
            //                    break;
            //                } else {
            //                    endDate = beginDate;
            //                }
            //            }
        }
    }

    public static Set<String> hasGrab() {
        File file = new File(TRADE_DAY_PATH);
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
        String fileName = TRADE_DAY_PATH + stock;
        File file = new File(fileName);
        if (file.exists()) {
            file.renameTo(new File(fileName + "_day"));
            System.out.println("rename " + stock + " finish");
            return;
        }
        System.out.println("rename " + stock + " is not exist");
    }

    public static String getLatestDay(String stock) throws Exception {
        File file = new File(TRADE_DAY_PATH + stock);
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
        File file = new File(TRADE_DAY_PATH + stock);

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
        //        try {
        //            driver.get("https://www.barchart.com/stocks/quotes/" + stock + "/interactive-chart");
        //        } catch (Exception e) {
        //            System.out.println("load timeout");
        //        }
        //
        //        while (true) {
        //            try {
        //                WebElement element = driver.findElement(By.className("chart-canvas-container"));
        //                return element;
        //            } catch (Exception e) {
        //                System.out.println("canvas not find");
        //            }
        //            TimeUnit.SECONDS.sleep(1);
        //        }
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
        //        TimeUnit.SECONDS.sleep(2);

        //        MoveInfo moveInfo = getMoveInfo(driver, actions);
        //        int xOffset = moveInfo.getXOffset();
        //        int avgStep = moveInfo.getAvgStep();
        //        getMoveData(driver, actions, xOffset, avgStep);
        return getMoveData(driver, actions, -514, 2);
        //        actions.moveByOffset(1, 0).perform();

        //        for (int i = 0; i < 10; i++) {
        //            actions.moveByOffset(10, 0).perform();
        //            String date = driver.findElement(By.xpath("//span[@class='field-name']")).getText();
        //            System.out.println(date);
        //            //            String text = driver.findElement(By.cssSelector("[style='color: #808080']")).getText();
        //            //            System.out.println(text);
        //            TimeUnit.MILLISECONDS.sleep(500);
        //        }
    }

    private static List<StockKLine> getMoveData(ChromeDriver driver, Actions actions, int xOffset, int step) {
        //        WebElement canvas = driver.findElement(By.className("chart-canvas-container"));
        //        actions.moveToElement(canvas, 0, 0).perform();
        actions.moveByOffset(xOffset, 0).perform();

        long begin = System.currentTimeMillis();
        String lastDate = null;
        int moveAdd = 0, moveMaxTimes = 15, moveTimes = 0, totalWidth = 980;
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
                //                System.out.println("retry");
                moveAdd += step;
                actions.moveByOffset(step, 0).perform();

                if (lastDate != null && moveAdd > totalWidth) {
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
                StockKLine kLine = StockKLine.builder()
                  .date(date)
                  .open(Double.valueOf(open))
                  .high(Double.valueOf(high))
                  .low(Double.valueOf(low))
                  .close(Double.valueOf(close))
                  .change(Double.valueOf(change))
                  .volume(BigDecimal.valueOf(Double.valueOf(volume.replace(",", ""))))
                  .build();
                dataList.add(kLine);
                //                System.out.println(kLine);

                lastDate = date;
            }
        }
        System.out.println("cost " + ((System.currentTimeMillis() - begin) / 1000) + "s");
        return dataList;
    }

    private static MoveInfo getMoveInfo(ChromeDriver driver, Actions actions) {
        int x = 0;
        int step = -1;
        String lastDate = null;
        int moveAdd = 0;
        int moveAddSum = 0;
        int moveAddTimes = 0;
        while (true) {
            String date = driver.findElement(By.xpath("//span[@class='field-name']")).getText();
            if (!StringUtils.equals(lastDate, date)) {
                System.out.println("left move date:" + date);
                System.out.println("moveAdd " + moveAdd);
                moveAddSum += moveAdd;
                moveAddTimes += 1;
                moveAdd = 0;
            }
            System.out.println("retry");

            x = x + step;
            moveAdd += step;
            actions.moveByOffset(step, 0).perform();

            //            if (lastDate != null && lastDate.equals(date) && moveAdd < -15) {
            if (lastDate != null && lastDate.equals(date) && StringUtils.isBlank(date)) {
                break;
            }

            lastDate = date;
        }
        int avgStep = moveAddSum / moveAddTimes;

        MoveInfo moveInfo = new MoveInfo();
        moveInfo.setAvgStep(-avgStep);
        moveInfo.setXOffset(moveAddSum + 2);

        System.out.println(moveInfo);
        return moveInfo;
    }

    private static void groupOpenDataByYear() throws Exception {
        Map<String, String> xnasOpenMap = BaseUtils.getOpenData("XNAS");

        Map<String/*year*/, Integer/*count*/> countMap = Maps.newHashMap();
        for (String code : xnasOpenMap.keySet()) {
            String open = xnasOpenMap.get(code);
            if (open.length() != 10) {
                continue;
            }
            String year = open.substring(0, 4);
            if (!countMap.containsKey(year)) {
                countMap.put(year, 0);
            }
            int count = countMap.get(year) + 1;
            countMap.put(year, count);
        }

        System.out.println(countMap);
    }

    @Data
    @ToString
    static class MoveInfo {
        private int avgStep;
        private int xOffset;
    }
}
