package bean;

import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import lombok.Data;
import org.apache.commons.lang3.StringUtils;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

@Data
public class StockRatio implements Serializable {

    Map<Integer, RatioBean> ratioMap = Maps.newHashMap();

    public void addBean(Bean bean) {
        double dn = bean.getDn();
        double low = bean.getLow();
        double open = bean.getOpen();
        if (!(low < dn && open < dn)) {
            return;
        }

        double openDnDiffPnt = bean.getOpenDnDiffPnt();
        int openDnDiffRange = (int) openDnDiffPnt;
        if (openDnDiffRange < 0) {
            return;
        }
        if (openDnDiffRange > 6) {
            if (!ratioMap.containsKey(6)) {
                ratioMap.put(6, new RatioBean());
            }
            ratioMap.get(6).add(bean);
        } else if (ratioMap.containsKey(openDnDiffRange)) {
            ratioMap.get(openDnDiffRange).add(bean);
        } else {
            RatioBean ratioBean = new RatioBean();
            ratioBean.add(bean);
            ratioMap.put(openDnDiffRange, ratioBean);
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
