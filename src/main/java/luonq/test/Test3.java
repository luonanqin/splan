package luonq.test;

import bean.NodeList;

public class Test3 {

    public static void main(String[] args) {
        NodeList list = new NodeList(5);

        list.add("a", 1);
        list.add("a", 2);
        list.show();

        list.add("b", 2);
        list.add("b", 0.5);
        list.show();

        list.add("c", 10);
        list.add("b", 3);
        list.show();

        list.add("d", 5);
        list.add("e", 6);
        list.show();

        list.add("b", 1);
        list.show();

        list.add("f", 11);
        list.show();

        list.add("f", 1);
        list.show();
    }
}
