package test;

import barchart.StockHistory;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.interactions.Actions;

import java.util.concurrent.TimeUnit;

/**
 * Created by Luonanqin on 2023/2/5.
 */
public class SeleniumTest {

    public static void main(String[] args) throws Exception{
        System.getProperties().setProperty("webdriver.chrome.driver", "chromedriver");
        ChromeOptions chromeOptions = new ChromeOptions();
        ChromeDriver driver = new ChromeDriver(chromeOptions);

        // login
        StockHistory.loginBarchart(driver);

        driver.get("https://www.barchart.com/stocks/quotes/PEP/interactive-chart");


        TimeUnit.SECONDS.sleep(5);
        WebElement canvas = driver.findElement(By.className("chart-canvas-container"));
        new Actions(driver)
          .moveToElement(canvas, 0, 0)
          .perform();
        for (int i = 0; i < 10; i++) {
            new Actions(driver)
              .moveByOffset(2, 0)
              .perform();
            String text = driver.findElement(By.cssSelector("[style='color: #808080']")).getText();
            System.out.println(text);
            TimeUnit.SECONDS.sleep(1);
        }

        System.out.println();
    }
}
