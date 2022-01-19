package io.github.czm23333.transparentreflect.example;

import io.github.czm23333.transparentreflect.annotations.Shadow;

@Shadow("cur/RefTargetSon")
public class ShadowRefSon extends ShadowRef {
    public ShadowRefSon(int i) {
        super(i);
    }

    @Shadow("my")
    public int my(int a) {
        return 0;
    }
}
