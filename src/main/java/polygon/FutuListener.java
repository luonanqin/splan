package polygon;

import bean.Node;
import bean.NodeList;
import com.futu.openapi.FTAPI;
import com.google.common.eventbus.Subscribe;
import futu.TradeApi;
import lombok.Data;

import java.util.List;

/**
 * Created by Luonanqin on 2023/5/9.
 */
@Data
public class FutuListener {

    private NodeList list;
    private TradeApi trdApi;

    public FutuListener() {
        FTAPI.init();
        trdApi = new TradeApi();
        trdApi.start();
    }

    @Subscribe
    public void onMessageEvent() {
        List<Node> nodes = list.getNodes();
        // 1.获取剩余可用现金
        double remainCash = trdApi.getFunds();
        // todo 2.用剩余可用现金计算可买数量
        // todo 3.用可买数量下市价单，如果可买数量为0，则执行56
        // todo 4.下单完成后，十秒后获取成交状态
        // todo 5.如果没有成交完成，则撤销剩下订单，并执行1
        // todo 6.计算之前已成交的止损价格，并设置止损市价单
    }
}
