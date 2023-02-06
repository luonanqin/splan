package util;

import com.google.common.collect.Maps;
import org.apache.commons.lang3.StringUtils;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.Map;

/**
 * Created by Luonanqin on 2023/2/5.
 */
public class BaseUtils {

    public static Map<String, String> getOpenData(String market) throws IOException {
        Map<String, String> openDate = Maps.newHashMap();
        BufferedReader openBr = new BufferedReader(new FileReader("src/main/resources/historicalData/open/" + market + ".txt"));
        String open;
        while (StringUtils.isNotBlank(open = openBr.readLine())) {
            String[] split = open.split("\t");
            String code = split[0];
            String date = split[1];
            if (StringUtils.isBlank(date) || date.equalsIgnoreCase("null")) {
                continue;
            }

            openDate.put(code, date);
        }
        return openDate;
    }

}
