package luonq.test;

import com.huaban.analysis.jieba.JiebaSegmenter;

import java.util.List;

public class SplitWordTest {

    public static void main(String[] args) {
        String content = "\"爱他美卓萃-带着自护力去挑战\n"
          + "伊利绮炫-红薯美食俱乐部\n"
          + "VISA-灵感创意大赛\n"
          + "雷诺考特-灵感创意大赛\n"
          + "茄皇-统一茄皇x没想到事务所\n"
          + "伊利绮炫-红薯美食俱乐部\n"
          + "统一双萃—没想到事务所\n"
          + "LA MER—520为爱启航\n"
          + "以上项目寻求闻毅koc合作\"";
        JiebaSegmenter segmenter = new JiebaSegmenter();
        List<String> result = segmenter.sentenceProcess(content);
        System.out.println(result);
    }
}
