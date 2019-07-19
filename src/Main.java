public class Main {

    public static void main(String[] args) {
        System.out.println(Integer.toBinaryString(-1 << 29));
        System.out.println(Integer.toBinaryString(0 << 29));
        System.out.println(Integer.toBinaryString((1 << 29) - 1));
        System.out.println(Integer.toBinaryString(2 << 29));
        System.out.println(Integer.toBinaryString(3 << 29));

        System.out.println(-1 << 29);
        System.out.println(0 << 29);
        System.out.println(((1 << 29) - 1));
        System.out.println(2 << 29);
        System.out.println(3 << 29);
    }
}
