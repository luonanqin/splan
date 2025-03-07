package luonq.test;

public class Test3 {

    public static void main(String[] args) {
        double init = 10000;
        for (int i = 0; i < 200; i++) {
            init = init * 1.01;
        }
        System.out.println(init);
    }
}
