package luonq.a;

public class Test {

    public static void main(String[] args) {
        int init = 10000;
        for (int i = 0; i < 20; i++) {
            int half = init / 2;
            int grow = (int) (half * 1.3);
            init = half + grow;
        }
        System.out.println(init);

        System.out.println((int) (7000 * Math.pow(1.3, 20)));
    }
}
