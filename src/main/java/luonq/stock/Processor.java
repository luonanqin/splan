package luonq.stock;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import lombok.extern.slf4j.Slf4j;
import luonq.futu.GetRehab;
import luonq.polygon.GetHistoricalDaily;
import luonq.polygon.GetHistoricalOpenFirstTrade;
import luonq.polygon.GetHistoricalTrade2;
import luonq.polygon.GrabEarning;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class Processor {

    public static void getData() throws Exception {
        log.info("GetHistoricalDaily.getData start");
        long s2 = System.currentTimeMillis();
        GetHistoricalDaily.getData();
        long e2 = System.currentTimeMillis();
        log.info("GetHistoricalDaily.getData end. cost: " + (e2 - s2) / 1000 + "s\n");

        log.info("MergeKline.merge start");
        long s3 = System.currentTimeMillis();
        MergeKline.merge();
        long e3 = System.currentTimeMillis();
        log.info("MergeKline.merge end. cost: " + (e3 - s3) / 1000 + "s\n");

        log.info("BollingerForYear.calculate start");
        long s4 = System.currentTimeMillis();
        BollingerForYear.calculate();
        long e4 = System.currentTimeMillis();
        log.info("BollingerForYear.calculate end. cost: " + (e4 - s4) / 1000 + "s\n");

        log.info("MergeBollingerForYear.calculate start");
        long s8 = System.currentTimeMillis();
        MergeBollingerForYear.calculate();
        long e8 = System.currentTimeMillis();
        log.info("MergeBollingerForYear.calculate end. cost: " + (e8 - s8) / 1000 + "s\n");

        log.info("GetHistoricalTrade.getData start");
        long s5 = System.currentTimeMillis();
        GetHistoricalTrade2.getData();
        long e5 = System.currentTimeMillis();
        log.info("GetHistoricalTrade.getData end. cost: " + (e5 - s5) / 1000 + "s\n");

        log.info("GetHistoricalOpenFirstTrade.getData start");
        long s6 = System.currentTimeMillis();
        GetHistoricalOpenFirstTrade.getData();
        long e6 = System.currentTimeMillis();
        log.info("GetHistoricalOpenFirstTrade.getData end. cost: " + (e6 - s6) / 1000 + "s\n");

        log.info("BollingerWithOpen.calculate start");
        long s7 = System.currentTimeMillis();
        OpenBollingerForYear.calculate();
        long e7 = System.currentTimeMillis();
        log.info("BollingerWithOpen.calculate end. cost: " + (e7 - s7) / 1000 + "s\n");

        log.info("MergeOpenBollingerForYear.calculate start");
        long s9= System.currentTimeMillis();
        MergeOpenBollingerForYear.calculate();
        long e9 = System.currentTimeMillis();
        log.info("MergeOpenBollingerForYear.calculate end. cost: " + (e9 - s9) / 1000 + "s\n");

        log.info("get data finish");
    }

    public static void getRehab() throws Exception{
        log.info("GetRehab.getData start");
        long s1 = System.currentTimeMillis();
        GetRehab.getData();
        long e1 = System.currentTimeMillis();
        log.info("GetRehab.getData end. cost: " + (e1 - s1) / 1000 + "s\n");
    }

    public static void getEarning() throws Exception {
        log.info("GrabEarning.getData start");
        long s8 = System.currentTimeMillis();
        GrabEarning.getData();
        long e8 = System.currentTimeMillis();
        log.info("GrabEarning.getData end. cost: " + (e8 - s8) / 1000 + "s\n");
    }

    public static void main(String[] args) throws Exception {
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("org.apache.commons").setLevel(Level.ERROR);
        ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger("httpclient.wire").setLevel(Level.ERROR);
        getData();
    }
}
