package io.github.czm23333.TransparentReflect.example;

import io.github.czm23333.TransparentReflect.annotations.Shadow;
import io.github.czm23333.TransparentReflect.annotations.ShadowGetter;
import io.github.czm23333.TransparentReflect.annotations.ShadowSetter;

@Shadow("cur/RefTarget")
public class ShadowRef {
    public ShadowRef(Object o) {}

    public ShadowRef(int i) {}

    @Shadow("c")
    public static int m3(int i, int j) {
        return 0;
    }

    @Shadow("a")
    public void m1() {}

    @Shadow("b")
    public int m2() {
        return 0;
    }

    @ShadowGetter("i3")
    public static int getF2() {
        return 0;
    }

    @ShadowSetter("i3")
    public static void setF2(int i) {}

    @ShadowGetter("i2")
    public int getF1() {
        return 0;
    }

    @ShadowSetter("i2")
    public void setF1(int i) {}
}
