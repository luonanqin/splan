package luonq.job;

import bean.TradeCalendar;
import lombok.extern.slf4j.Slf4j;
import luonq.data.ReadFromDB;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import util.Constants;

import java.time.LocalDate;

@Component
@Slf4j
public class BaseJob {

    @Autowired
    private ReadFromDB readFromDB;

    public boolean noTrade() {
        String today = LocalDate.now().format(Constants.DB_DATE_FORMATTER);
        TradeCalendar tradeCalendar = readFromDB.getTradeCalendar(today);
        if (tradeCalendar == null) {
            log.info("no trading today!");
            return true;
        }
        return false;
    }
}
