package io.github.czm23333.TransparentReflect.example;

import io.github.czm23333.TransparentReflect.annotations.ShadowExtend;
import io.github.czm23333.TransparentReflect.annotations.ShadowOverride;

@ShadowExtend("cur/RefTarget2")
public class ShadowExtendRef2 {
    @ShadowOverride("m1")
    public ShadowRef m1(ShadowRef ref) {
        System.out.println("om1");
        return new ShadowRef(ref.getF1());
    }
}
