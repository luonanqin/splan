package luonq.test;

import util.BaseUtils;
import util.Constants;

import java.util.List;
import java.util.Map;

/**
 * Created by Luonanqin on 2023/3/26.
 */
public class Test2 {

    public static void main(String[] args) throws Exception {
        Map<String, String> hasOption = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "code/hasOption");
        Map<String, String> all = BaseUtils.getFileMap(Constants.HIS_BASE_PATH + "code/list");

        for (String market : all.keySet()) {
            String allFile = all.get(market);
            String hasFile = hasOption.get(market);

            List<String> allCode = BaseUtils.readFile(allFile);
            List<String> hasCode = BaseUtils.readFile(hasFile);

            for (String code : allCode) {
                if (!hasCode.contains(code)) {
                    System.out.println(market + " " + code);
                }
            }
        }
    }
}
