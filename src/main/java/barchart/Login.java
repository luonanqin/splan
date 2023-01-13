package barchart;

import com.google.common.collect.Lists;
import org.openqa.selenium.By;
import org.openqa.selenium.WebElement;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;

import java.util.List;

public class Login {

    public static void main(String[] args) {
        System.getProperties().setProperty("webdriver.chrome.driver", "chromedriver");
        ChromeOptions chromeOptions = new ChromeOptions();
        ChromeDriver driver = new ChromeDriver(chromeOptions);
        driver.get("https://www.barchart.com//");
        WebElement loginBtn = driver.findElement(By.className("login"));
        loginBtn.click();

        // login
        driver.findElement(By.cssSelector("[name='email']")).sendKeys("qinnanluo@sina.com");
        driver.findElement(By.cssSelector("[name='password']")).sendKeys("luonq134931");
        driver.findElement(By.className("bc-nui-modal-login__button")).click();

        // download historical stock data
        List<String> stockList = Lists.newArrayList("AAPL", "AMZN", "AMD");
        for (String stock : stockList) {
            driver.get("https://www.barchart.com/my/price-history/download/" + stock);
            driver.findElement(By.xpath("//a[@data-historical='historical']")).click();
        }

        driver.quit();
    }

}
