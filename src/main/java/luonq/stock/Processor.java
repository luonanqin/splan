package luonq.stock;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import luonq.futu.GetRehab;
import luonq.indicator.BollingerWithOpen;
import luonq.polygon.GetHistoricalDaily;
import luonq.polygon.GetHistoricalOpenFirstTrade;
import luonq.polygon.GetHistoricalTrade;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class Processor {

    @Scheduled(cron = "0 03 21 * * ?")
    public static void getData() throws Exception {
        System.out.println("GetRehab.getData start");
        long s1 = System.currentTimeMillis();
        GetRehab.getData();
        long e1 = System.currentTimeMillis();
        System.out.println("GetRehab.getData end. cost: " + (e1 - s1) / 1000 + "s\n");

        System.out.println("GetHistoricalDaily.getData start");
        long s2 = System.currentTimeMillis();
        GetHistoricalDaily.getData();
        long e2 = System.currentTimeMillis();
        System.out.println("GetHistoricalDaily.getData end. cost: " + (e2 - s2) / 1000 + "s\n");

        System.out.println("MergeKline.merge start");
        long s3 = System.currentTimeMillis();
        MergeKline.merge();
        long e3 = System.currentTimeMillis();
        System.out.println("MergeKline.merge end. cost: " + (e3 - s3) / 1000 + "s\n");

        System.out.println("MergeBollinger.calculate start");
        long s4 = System.currentTimeMillis();
        MergeBollinger.calculate();
        long e4 = System.currentTimeMillis();
        System.out.println("MergeBollinger.calculate end. cost: " + (e4 - s4) / 1000 + "s\n");


        System.out.println("GetHistoricalTrade.getData start");
        long s5 = System.currentTimeMillis();
        GetHistoricalTrade.getData();
        long e5 = System.currentTimeMillis();
        System.out.println("GetHistoricalTrade.getData end. cost: " + (e5 - s5) / 1000 + "s\n");

        System.out.println("GetHistoricalOpenFirstTrade.getData start");
        long s6 = System.currentTimeMillis();
        GetHistoricalOpenFirstTrade.getData();
        long e6 = System.currentTimeMillis();
        System.out.println("GetHistoricalOpenFirstTrade.getData end. cost: " + (e6 - s6) / 1000 + "s\n");

        System.out.println("BollingerWithOpen.calculate start");
        long s7 = System.currentTimeMillis();
        BollingerWithOpen.calculate();
        long e7 = System.currentTimeMillis();
        System.out.println("BollingerWithOpen.getData end. cost: " + (e7 - s7) / 1000 + "s\n");

        System.out.println("get data finish");
    }

    public static void main(String[] args) throws Exception {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.commons").setLevel(Level.ERROR);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("httpclient.wire").setLevel(Level.ERROR);
        getData();
    }
}
