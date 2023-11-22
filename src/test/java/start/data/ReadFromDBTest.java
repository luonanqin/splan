package start.data;

import bean.Page;
import bean.Total;
import com.google.common.collect.Lists;
import lombok.extern.slf4j.Slf4j;
import luonq.mapper.StockDataMapper;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.springframework.beans.factory.annotation.Autowired;
import start.BaseTest;

import java.util.List;

@Slf4j
public class ReadFromDBTest extends BaseTest {

    @Autowired
    private StockDataMapper stockDataMapper;

    @Before
    public void before() {
        log.info("begin");
    }

    @After
    public void after() {
        log.info("after");
    }

    @Test
    public void queryForAllYear() throws Exception {
        Page page = new Page();
        List<Total> allTotals = Lists.newLinkedList();
        while (true) {
            List<Total> totals = stockDataMapper.queryForAllYear("2023", page);
            int size = totals.size();
            if (size == 0) {
                break;
            }
            page.setId(totals.get(size - 1).getId());
            allTotals.addAll(totals);
        }
        System.out.println(allTotals.size());
    }
}