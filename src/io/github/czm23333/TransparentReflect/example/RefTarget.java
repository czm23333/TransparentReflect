package io.github.czm23333.TransparentReflect.example;

public class RefTarget {
    private final int i1;
    public int i2 = 0;
    public static int i3 = 0;

    protected int i4 = 1;

    public RefTarget(int i) {
        i1 = i;
    }

    public void a() {
        System.out.println(i1);
    }

    public int b() {
        return i2;
    }

    public static int c(int add, int add2) {
        return i3 + add + add2;
    }

    protected int d() {
        return i4 + 6;
    }
}
