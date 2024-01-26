package bean;

import com.google.common.collect.Lists;
import lombok.Data;

import java.io.Serializable;
import java.util.List;

@Data
public class RatioBean implements Serializable {

    List<Bean> beanList = Lists.newArrayList();
    double ratio;

    public void add(Bean bean) {
        beanList.add(bean);
        long trueCount = beanList.stream().filter(c -> c.getCloseGreatOpen() == 1).count();
        int count = beanList.size();
        ratio = (double) trueCount / count;
    }

}
