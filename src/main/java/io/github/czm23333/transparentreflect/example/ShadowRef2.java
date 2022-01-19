package io.github.czm23333.transparentreflect.example;

import io.github.czm23333.transparentreflect.annotations.Shadow;

@Shadow("cur/RefTarget2")
public class ShadowRef2 {
    public ShadowRef2(Object o) {}

    @Shadow("m1")
    public ShadowRef m1(ShadowRef ref) {
        return null;
    }
}
