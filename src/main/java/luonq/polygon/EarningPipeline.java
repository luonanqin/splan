package luonq.polygon;

import com.google.common.collect.Lists;
import lombok.Getter;
import us.codecraft.webmagic.ResultItems;
import us.codecraft.webmagic.Task;
import us.codecraft.webmagic.pipeline.Pipeline;

import java.util.List;

public class EarningPipeline implements Pipeline {

    @Getter
    private List<String> res = Lists.newArrayList();

    @Override
    public void process(ResultItems resultItems, Task task) {
        Object o = resultItems.get("");
        if (o == null) {
            return;
        }

        res.addAll((List<String>) o);
    }
}
