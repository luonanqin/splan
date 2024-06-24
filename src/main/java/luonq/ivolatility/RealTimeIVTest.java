package luonq.ivolatility;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import util.BaseUtils;
import util.Constants;

import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;
import java.util.Map;

public class RealTimeIVTest {

    public static void main(String[] args) throws Exception {
        List<String> lines = BaseUtils.readFile(Constants.USER_PATH + "optionData/rtTest");

        Map<String, List<String>> map = Maps.newHashMap();

        for (String line : lines) {
            String[] split = line.split("\t");
            String stock = split[0];
            String time = split[3];
            Date date = new Date();
            date.setTime(Long.valueOf(time));
            String formatTime = LocalDateTime.ofInstant(date.toInstant(), ZoneId.systemDefault()).format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            if (!map.containsKey(stock)) {
                map.put(stock, Lists.newArrayList());
            }

            map.get(stock).add(line + "\t" + formatTime);
        }

        for (String stock : map.keySet()) {
            List<String> list = map.get(stock);
            //            Collections.sort(list, (o1, o2) -> (int) (Long.valueOf(o1.split("\t")[3]) - Long.valueOf(o2.split("\t")[3])));
            //
            list.forEach(l -> System.out.println(l));
            System.out.println();
        }
    }
}
