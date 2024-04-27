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
import util.Constants;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.Executor;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static util.Constants.HIS_BASE_PATH;

/**
 * Created by Luonanqin on 2023/2/5.
 */
public class FixGrabStockDateHistory {

    public static void main(String[] args) throws Exception {
        System.getProperties().setProperty("webdriver.chrome.driver", "chromedriver");

        // 等待抓取修复的
        Map<String, String> waitFixGrabMap = waitFixGrab();
        //        deleteFile(waitFixGrabMap);

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

        int corePoolSize = threadCount;
        int maximumPoolSize = corePoolSize;
        long keepAliveTime = 60L;
        LinkedBlockingQueue<Runnable> workQueue = new LinkedBlockingQueue<>();
        Executor cachedThread = new ThreadPoolExecutor(corePoolSize, maximumPoolSize, keepAliveTime, TimeUnit.MILLISECONDS, workQueue);
        Set<String> tempStock = Sets.newHashSet("DRQ", "AMG", "CHH", "PB", "GPI", "LAD", "INGR", "KEX", "PDS", "OCN", "SRI", "GIB", "TGI", "SPIR", "UNFI", "EPR", "SAH", "MTD", "FDP", "URI", "GEL", "TR", "PAG", "TDW", "NUS");
        for (String stock : waitFixGrabMap.keySet()) {
            if (!tempStock.contains(stock)) {
                //                continue;
            }
            System.out.println(stock);
            ChromeDriver driver = driverQueue.take();
            cachedThread.execute(() -> asyncProcess(driverQueue, driver, stock, waitFixGrabMap));
            //            asyncProcess(driverQueue, driver, stock, waitFixGrabMap);
        }
        System.out.println("end");
    }

    public static void asyncProcess(BlockingQueue<ChromeDriver> driverQueue, ChromeDriver driver, String stock, Map<String, String> waitFixGrabMap) {
        try {
            String range = waitFixGrabMap.get(stock);

            String[] split = range.split(", ");
            List<String> rangeList = Lists.newArrayList();

            String hasFixFileDir = HIS_BASE_PATH + "fixGrab/" + stock + "/";
            File dir = new File(hasFixFileDir);
            if (!dir.exists()) {
                dir.mkdirs();
            }

            String[] fileList = dir.list();
            Set<String> fileSet = Arrays.stream(fileList).collect(Collectors.toSet());
            for (String rangeStr : split) {
                String[] rangeDate = rangeStr.split("~");

                String beginDate = rangeDate[0];
                String endDate = rangeDate[1];

                String hasFixFile = beginDate.replaceAll("/", "") + "_" + endDate.replaceAll("/", "") + "_day";
                if (fileSet.contains(hasFixFile)) {
                    //                    continue;
                } else {
                    rangeList.add(rangeStr);
                }
            }

            if (CollectionUtils.isEmpty(rangeList)) {
                return;
            }

            WebElement canvas = loadStockCanvas(driver, stock);

            for (String rangeStr : rangeList) {
                String[] rangeDate = rangeStr.split("~");

                String beginDate = rangeDate[0];
                String endDate = rangeDate[1];

                String hasFixFile = HIS_BASE_PATH + "fixGrab/" + stock + "/" + beginDate.replaceAll("/", "") + "_" + endDate.replaceAll("/", "") + "_day";
                setDateRange(driver, beginDate, endDate);
                System.out.println("confirm new date range: " + stock + " " + beginDate + " - " + endDate);
                List<StockKLine> dataList = getDataFromCanvas(driver, canvas);

                if (CollectionUtils.isNotEmpty(dataList)) {
                    writeFixDay(hasFixFile, Lists.reverse(dataList));
                } else {
                    writeFixDay(hasFixFile, Lists.newArrayList());
                }
                System.out.println("write finish: " + stock + " " + beginDate + " - " + endDate);
            }
        } catch (Exception e) {
            e.printStackTrace();
            System.out.println(e.getMessage());
        } finally {
            driverQueue.offer(driver);
        }
    }

    public static Map<String, String> waitFixGrab() throws Exception {
        String filePath = Constants.TEST_PATH + "waitFixGrab";
        List<String> lineList = BaseUtils.readFile(filePath);
        Map<String, String> map = Maps.newHashMap();
        for (String line : lineList) {
            String[] split = line.split("\t");
            map.put(split[0], split[1]);
        }
        return map;
    }

    public static void renameFile(String stock) {
        String fileName = HIS_BASE_PATH + "/grabFix" + stock;
        File file = new File(fileName);
        if (file.exists()) {
            file.renameTo(new File(fileName + "_day"));
            System.out.println("rename " + stock + " finish");
            return;
        }
        System.out.println("rename " + stock + " is not exist");
    }

    public static void writeFixDay(String fileName, List<StockKLine> dataList) throws Exception {
        File file = new File(fileName);
        if (!file.exists()) {
            file.createNewFile();
        }

        FileOutputStream fos = new FileOutputStream(file);
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
        //        TimeUnit.SECONDS.sleep(2);

        //        MoveInfo moveInfo = getMoveInfo(driver, actions);
        //        int xOffset = moveInfo.getXOffset();
        //        int avgStep = moveInfo.getAvgStep();
        //        getMoveData(driver, actions, xOffset, avgStep);
        return getMoveData(driver, actions, -520, 1);
    }

    private static List<StockKLine> getMoveData(ChromeDriver driver, Actions actions, int xOffset, int step) {
        //        WebElement canvas = driver.findElement(By.className("chart-canvas-container"));
        //        actions.moveToElement(canvas, 0, 0).perform();
        actions.moveByOffset(xOffset, 0).perform();

        long begin = System.currentTimeMillis();
        String lastDate = null;
        int moveAdd = 0, moveMaxTimes = 15, moveTimes = 0, totalWidth = 990;
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

    private static void deleteFile(Map<String, String> waitForGrab) {
        Set<String> set = Sets.newHashSet();
        String path = HIS_BASE_PATH + "fixGrab/";
        for (String stock : waitForGrab.keySet()) {
            String rangeStr = waitForGrab.get(stock);
            String[] rangeList = rangeStr.split(", ");
            for (String range : rangeList) {
                range = range.replaceAll("/", "").replaceAll("~", "_");
                if (set.contains(stock + range + "_day")) {
                    continue;
                }
                File file = new File(path + stock + "/" + range + "_day");
                if (file.exists()) {
                    file.delete();
                }
            }
        }
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

    @Data
    @ToString
    static class MoveInfo {
        private int avgStep;
        private int xOffset;
    }
}
