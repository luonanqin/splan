package luonq.polygon;

import com.google.common.collect.Lists;
import util.BaseUtils;

import java.util.List;
import java.util.Map;

public class ReverseData {

    public static void main(String[] args) throws Exception{
        Map<String, String> fileMap = BaseUtils.getFileMap("/Users/Luonanqin/study/intellij_idea_workspaces/temp/week/");

        for (String stock : fileMap.keySet()) {
            if (stock.equals("AAPL")) {
                continue;
            }

            String filePath = fileMap.get(stock);
            List<String> lineList = BaseUtils.readFile(filePath);

            List<String> newLineList = Lists.reverse(lineList);
            BaseUtils.writeFile(filePath, newLineList);
        }
    }
}
