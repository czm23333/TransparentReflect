package io.github.czm23333.transparentreflect.example;

import io.github.czm23333.transparentreflect.annotations.*;

@ShadowExtend("cur/RefTarget")
public class ShadowRefAcccessor {
    @DisabledConstructor
    public ShadowRefAcccessor() {}

    @ShadowAccessor("d")
    public static int accD(ShadowRef obj) {
        return 0;
    }

    @ShadowAccessorGetter("i4")
    public static int getI4(ShadowRef obj) {
        return 0;
    }

    @ShadowAccessorSetter("i4")
    public static void setI4(ShadowRef obj, int i) {}
}