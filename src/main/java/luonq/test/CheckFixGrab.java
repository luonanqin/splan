package luonq.test;

import util.BaseUtils;
import util.Constants;

import java.io.File;
import java.util.List;

/**
 * Created by Luonanqin on 2023/3/22.
 */
public class CheckFixGrab {

    public static void main(String[] args) throws Exception {
        String basePath = Constants.HIS_BASE_PATH + "fixGrab";
        File file = new File(basePath);
        for (File stockDir : file.listFiles()) {
            File[] fileList = stockDir.listFiles();
            if (fileList == null) {
                continue;
            }

            for (File f : fileList) {
                List<String> lineList = BaseUtils.readFile(f);
                if (lineList.isEmpty()) {
//                    System.out.println(stockDir.getName() + " empty file :" + f.getPath());
                }
                if (lineList.size() == 1) {
                    System.out.println(stockDir.getName() + " one line file :" + f.getPath());
                    f.delete();
                }
            }
        }
    }
}
