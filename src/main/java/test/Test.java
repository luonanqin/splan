package test;

import com.google.common.collect.Lists;
import util.BaseUtils;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * Created by Luonanqin on 2023/2/9.
 */
public class Test {

    public static void main(String[] args) throws Exception {
        //        List<String> markets = Lists.newArrayList("XNAS-ADRC","XNYS-ADRC");
        //        List<String> markets = Lists.newArrayList("XNAS","XNYS");
        List<String> markets = Lists.newArrayList("XNAS");

        int size = 100;
        int sum = 0;
        long i = 1000 * 3600 * 24;
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd");

        long nowTime = format.parse("2023-01-01").getTime();
        long _2000 = format.parse("2000-01-01").getTime();

        for (String market : markets) {
            Map<String, String> openMap = BaseUtils.getOpenData(market);
            for (String stock : openMap.keySet()) {
                String open = openMap.get(stock);
                Date openDate = format.parse(open);
                long openTime = openDate.getTime();
                if (openTime < _2000) {
                    openTime = _2000;
                }

                int count = Math.abs((int) ((nowTime - openTime) / i));
                //                System.out.println(stock + " " + count);
                sum += count;
                size--;
                if (size < 0) {
//                    break;
                }
            }
            System.out.println(market + " " + sum);
        }
    }
}
