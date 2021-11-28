package io.github.czm23333.TransparentReflect.example;

import io.github.czm23333.TransparentReflect.annotations.Shadow;
import io.github.czm23333.TransparentReflect.annotations.ShadowExtend;
import io.github.czm23333.TransparentReflect.annotations.ShadowOverride;
import io.github.czm23333.TransparentReflect.annotations.ShadowSetter;

@ShadowExtend("cur/RefTarget")
public class ShadowExtendRef {
    public final ShadowRef self;

    public ShadowExtendRef(int i) {
        self = new ShadowRef(this);
        System.out.println("Init!");
    }

    @ShadowOverride("a")
    public void a() {
        System.out.println("Hello");
    }

    @ShadowSetter("i4")
    public void s1(int i) {}

    @Shadow("d")
    public int s2() {
        return 0;
    }
}
