package slobben.cells.util;

public class Utils {
    public static int makeEven(int numberToMakeEven) {
        if (numberToMakeEven % 2 == 0) {
            return numberToMakeEven;
        }
        return numberToMakeEven - 1;
    }
}
