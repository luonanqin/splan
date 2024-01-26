package bean;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
public class StockUpRatio implements Serializable {

    Map<Integer, RatioBean> ratioMap = Maps.newHashMap();

    public void addBean(Bean bean) {
        double up = bean.getUp();
        double high = bean.getHigh();
        double open = bean.getOpen();
        if (!(high > up && open > up)) {
            return;
        }

        double openUpDiffPnt = bean.getOpenUpDiffPnt();
        int openUpDiffRange = (int) openUpDiffPnt;
        if (openUpDiffRange < 0) {
            return;
        }
        if (openUpDiffRange > 6) {
            if (!ratioMap.containsKey(6)) {
                ratioMap.put(6, new RatioBean());
            }
            ratioMap.get(6).add(bean);
        } else if (ratioMap.containsKey(openUpDiffRange)) {
            ratioMap.get(openUpDiffRange).add(bean);
        } else {
            RatioBean ratioBean = new RatioBean();
            ratioBean.add(bean);
            ratioMap.put(openUpDiffRange, ratioBean);
        }
    }

    public String toString() {
        List<String> s = Lists.newArrayList();
        for (Integer ratio : ratioMap.keySet()) {
            s.add(String.format("%d=%.3f", ratio, ratioMap.get(ratio).getRatio()));
        }
        return StringUtils.join(s, ",");
    }

}
