package io.github.czm23333.transparentreflect.example;

import io.github.czm23333.transparentreflect.annotations.ShadowExtend;
import io.github.czm23333.transparentreflect.annotations.ShadowOverride;

@ShadowExtend("cur/RefTarget2")
public class ShadowExtendRef2 {
    @ShadowOverride("m1")
    public ShadowRef m1(ShadowRef ref) {
        System.out.println("om1");
        return new ShadowRef(ref.getF1());
    }
}
