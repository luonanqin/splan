package test;

import java.util.Random;

public class Test3 {

    public static void main(String[] args) {
        NodeList list = new NodeList(10);
        Random random = new Random(System.currentTimeMillis());

        long s = System.currentTimeMillis();
        for (int i = 0; i < 1000; i++) {
            double v = random.nextDouble();
            list.add(v);
        }
        System.out.println(System.currentTimeMillis() - s);
        System.out.println(list.show());
    }
}
