package barchart;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import com.google.common.collect.Lists;
import org.openqa.selenium.By;
import org.openqa.selenium.Dimension;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.slf4j.LoggerFactory;
import util.BaseUtils;
import util.Constants;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Created by Luonanqin on 2023/2/5.
 */
public class GrabEarningHistory {

    public static void main(String[] args) throws Exception {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.commons").setLevel(Level.ERROR);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("httpclient.wire").setLevel(Level.ERROR);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.asynchttpclient").setLevel(Level.ERROR);
        System.getProperties().setProperty("webdriver.chrome.driver", "chromedriver");

        ChromeOptions chromeOptions = new ChromeOptions();
        chromeOptions.addArguments("--remote-allow-origins=*");
        //        chromeOptions.addArguments("--whitelisted-ips=*");
        ChromeDriver driver = new ChromeDriver(chromeOptions);
        driver.manage().window().setSize(new Dimension(1280, 1027));
        driver.manage().timeouts().pageLoadTimeout(10, TimeUnit.SECONDS);

        List<String> dayList = Lists.newArrayList("2022-12-30","2022-12-29","2022-12-28","2022-12-27","2022-12-23","2022-12-22","2022-12-21","2022-12-20","2022-12-19","2022-12-16","2022-12-15","2022-12-14","2022-12-13","2022-12-12","2022-12-09","2022-12-08","2022-12-07","2022-12-06","2022-12-05","2022-12-02","2022-12-01","2022-11-30","2022-11-29","2022-11-28","2022-11-25","2022-11-23","2022-11-22","2022-11-21","2022-11-18","2022-11-17","2022-11-16","2022-11-15","2022-11-14","2022-11-11","2022-11-10","2022-11-09","2022-11-08","2022-11-07","2022-11-04","2022-11-03","2022-11-02","2022-11-01","2022-10-31","2022-10-28","2022-10-27","2022-10-26","2022-10-25","2022-10-24","2022-10-21","2022-10-20","2022-10-19","2022-10-18","2022-10-17","2022-10-14","2022-10-13","2022-10-12","2022-10-11","2022-10-10","2022-10-07","2022-10-06","2022-10-05","2022-10-04","2022-10-03","2022-09-30","2022-09-29","2022-09-28","2022-09-27","2022-09-26","2022-09-23","2022-09-22","2022-09-21","2022-09-20","2022-09-19","2022-09-16","2022-09-15","2022-09-14","2022-09-13","2022-09-12","2022-09-09","2022-09-08","2022-09-07","2022-09-06","2022-09-02","2022-09-01","2022-08-31","2022-08-30","2022-08-29","2022-08-26","2022-08-25","2022-08-24","2022-08-23","2022-08-22","2022-08-19","2022-08-18","2022-08-17","2022-08-16","2022-08-15","2022-08-12","2022-08-11","2022-08-10","2022-08-09","2022-08-08","2022-08-05","2022-08-04","2022-08-03","2022-08-02","2022-08-01","2022-07-29","2022-07-28","2022-07-27","2022-07-26","2022-07-25","2022-07-22","2022-07-21","2022-07-20","2022-07-19","2022-07-18","2022-07-15","2022-07-14","2022-07-13","2022-07-12","2022-07-11","2022-07-08","2022-07-07","2022-07-06","2022-07-05","2022-07-01","2022-06-30","2022-06-29","2022-06-28","2022-06-27","2022-06-24","2022-06-23","2022-06-22","2022-06-21","2022-06-17","2022-06-16","2022-06-15","2022-06-14","2022-06-13","2022-06-10","2022-06-09","2022-06-08","2022-06-07","2022-06-06","2022-06-03","2022-06-02","2022-06-01","2022-05-31","2022-05-27","2022-05-26","2022-05-25","2022-05-24","2022-05-23","2022-05-20","2022-05-19","2022-05-18","2022-05-17","2022-05-16","2022-05-13","2022-05-12","2022-05-11","2022-05-10","2022-05-09","2022-05-06","2022-05-05","2022-05-04","2022-05-03","2022-05-02","2022-04-29","2022-04-28","2022-04-27","2022-04-26","2022-04-25","2022-04-22","2022-04-21","2022-04-20","2022-04-19","2022-04-18","2022-04-14","2022-04-13","2022-04-12","2022-04-11","2022-04-08","2022-04-07","2022-04-06","2022-04-05","2022-04-04","2022-04-01","2022-03-31","2022-03-30","2022-03-29","2022-03-28","2022-03-25","2022-03-24","2022-03-23","2022-03-22","2022-03-21","2022-03-18","2022-03-17","2022-03-16","2022-03-15","2022-03-14","2022-03-11","2022-03-10","2022-03-09","2022-03-08","2022-03-07","2022-03-04","2022-03-03","2022-03-02","2022-03-01","2022-02-28","2022-02-25","2022-02-24","2022-02-23","2022-02-22","2022-02-18","2022-02-17","2022-02-16","2022-02-15","2022-02-14","2022-02-11","2022-02-10","2022-02-09","2022-02-08","2022-02-07","2022-02-04","2022-02-03","2022-02-02","2022-02-01","2022-01-31","2022-01-28","2022-01-27","2022-01-26","2022-01-25","2022-01-24","2022-01-21","2022-01-20","2022-01-19","2022-01-18","2022-01-14","2022-01-13","2022-01-12","2022-01-11","2022-01-10","2022-01-07","2022-01-06","2022-01-05","2022-01-04","2022-01-03");

        for (String day : dayList) {
            By tableXpath = By.xpath("//div[@id='cal-res-table']");
            BaseUtils.viewloadPage(driver, "https://finance.yahoo.com/calendar/earnings?day=" + day, tableXpath);
            String resultCountText = driver.findElement(By.xpath("//span[@class='Mstart(15px) Fw(500) Fz(s)']")).getText();
            int ofIndex = resultCountText.indexOf("of");
            int resultCount = Integer.valueOf(resultCountText.substring(ofIndex + 3, resultCountText.length() - 8));

            int pageNo = (resultCount + 99) / 100;
            List<String> resList = Lists.newArrayList();
            for (int i = 0; i < pageNo; i++) {
                if (i == 0) {
                    resList.addAll(print(driver));
                } else {
                    BaseUtils.viewloadPage(driver, "https://finance.yahoo.com/calendar/earnings?day=" + day + "&offset=" + (100 * i) + "&size=100", tableXpath);
                    resList.addAll(print(driver));
                }
            }

            BaseUtils.writeFile(Constants.HIS_BASE_PATH + "earning/" + day, resList);
            System.out.println("finish " + day);
        }
    }

    private static List<String> print(ChromeDriver driver) {
        List<String> res = Lists.newArrayList();

        By tableXpath = By.xpath("//div[@id='cal-res-table']");
        WebElement element = driver.findElement(tableXpath);
        List<WebElement> symbolList = element.findElements(By.xpath("//td[@aria-label='Symbol']"));
        List<WebElement> timeList = element.findElements(By.xpath("//td[@aria-label='Earnings Call Time']"));
        for (int i = 0; i < symbolList.size(); i++) {
            String symbol = symbolList.get(i).getText();
            String time = timeList.get(i).getText();
            res.add(symbol + " " + time);
        }

        return res;
    }
}
