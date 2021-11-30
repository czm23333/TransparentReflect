package io.github.czm23333.transparentreflect.example;

public class RefTarget2 {
    public RefTarget m1(RefTarget refTarget) {
        System.out.println("m1");
        return new RefTarget(refTarget.d());
    }
}
