package io.github.czm23333.TransparentReflect.example;

import io.github.czm23333.TransparentReflect.ShadowManager;

public class Main {
    public static void main(String[] args) throws Throwable {
        ShadowManager.root.createSubDirectory("cur", "io.github.czm23333.TransparentReflect.example");
        ShadowManager.initShadow(Main.class);

        ShadowRef shadow = new ShadowRef(1);
        shadow.m1();
        shadow.setF1(3);
        System.out.println(shadow.m2());
        ShadowRef.setF2(5);
        System.out.println(ShadowRef.m3(3, 5));

        ShadowRefSon son = new ShadowRefSon(1);
        son.setF1(5);
        System.out.println(son.my(8));
        son.m1();

        ShadowExtendRef extend = new ShadowExtendRef(1);
        shadow = new ShadowRef(extend);
        shadow.m1();
        extend.s1(5);
        System.out.println(extend.s2());

        ShadowExtendRef2 extend2 = new ShadowExtendRef2();
        new ShadowRef2(extend2).m1(new ShadowRef(1)).m1();
    }
}
