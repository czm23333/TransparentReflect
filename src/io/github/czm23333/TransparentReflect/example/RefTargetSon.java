package io.github.czm23333.TransparentReflect.example;

public class RefTargetSon extends RefTarget {
    public RefTargetSon(int i) {
        super(i);
    }

    public int my(int a) {
        return super.i2 + 114514 + a;
    }
}
