package io.github.czm23333.transparentreflect;

import io.github.czm23333.transparentreflect.annotations.*;
import io.github.czm23333.transparentreflect.internal.ShadowInterface;
import javassist.*;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.MethodInfo;
import javassist.util.proxy.DefineClassHelper;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;
import org.reflections.util.FilterBuilder;

import java.lang.annotation.Annotation;
import java.util.*;
import java.util.function.Consumer;

public class ShadowManager {
    public static final Directory root = new Directory();

    private static final HashMap<String, Class<?>> shadowTargetMap = new HashMap<>();

    public static Class<?> indexToClass(String path) throws ClassNotFoundException {
        String[] parts = path.split("/");
        StringBuilder result = new StringBuilder();
        Directory curr = root;
        for (String part : parts) {
            if (!curr.isMark())
                result.append('.');
            curr = curr.hasSubDirectory(part) ? curr.getSubDirectory(part) : new Directory(part);
            result.append(curr.getRealPath());
        }
        return Class.forName(result.toString());
    }

    public static String indexTo(String path) {
        String[] parts = path.split("/");
        StringBuilder result = new StringBuilder();
        Directory curr = root;
        for (String part : parts) {
            curr = curr.hasSubDirectory(part) ? curr.getSubDirectory(part) : new Directory(part);
            result.append(curr.getRealPath());
        }
        return result.toString();
    }

    public static Object shadowUnpack(Object obj) {
        if (obj instanceof ShadowInterface)
            return ((ShadowInterface) obj).getRealObject();
        else
            return obj;
    }

    private static Set<String> scan(Class<? extends Annotation> annotation, ClassLoader cl, String pack) {
        Reflections reflections = new Reflections(new ConfigurationBuilder().forPackage(pack, cl)
                                                                            .setInputsFilter(
                                                                                    new FilterBuilder().includePackage(
                                                                                            pack))
                                                                            .setExpandSuperTypes(false));
        return reflections.get(Scanners.SubTypes.of(Scanners.TypesAnnotated.with(annotation)));
    }

    private static String perfectForward(CtClass[] para) {
        StringBuilder insertCode = new StringBuilder();
        for (int i = 1; i <= para.length; ++i) {
            CtClass paraClass = para[i - 1];
            if (shadowTargetMap.containsKey(paraClass.getName())) {
                insertCode.append('(');

                insertCode.append('(');
                insertCode.append(shadowTargetMap.get(paraClass.getName()).getName());
                insertCode.append(')');

                insertCode.append("(((");
                insertCode.append(ShadowInterface.class.getName());
                insertCode.append(')');
                insertCode.append('$');
                insertCode.append(i);
                insertCode.append(')');
                insertCode.append(".getRealObject())");

                insertCode.append(')');
            } else {
                insertCode.append('$');
                insertCode.append(i);
            }
            if (i != para.length)
                insertCode.append(',');
        }
        return insertCode.toString();
    }

    private static String perfectBackward(CtClass[] para) {
        StringBuilder insertCode = new StringBuilder();
        for (int i = 1; i <= para.length; ++i) {
            CtClass paraClass = para[i - 1];
            if (shadowTargetMap.containsKey(paraClass.getName())) {
                insertCode.append("new ");
                insertCode.append(paraClass.getName());
                insertCode.append('(');
                insertCode.append("(Object)");
                insertCode.append('$');
                insertCode.append(i);
                insertCode.append(')');
            } else {
                insertCode.append('$');
                insertCode.append(i);
            }
            if (i != para.length)
                insertCode.append(',');
        }
        return insertCode.toString();
    }

    public static void initShadow(ClassLoader cl, String packageName, Consumer<CtClass> definer, boolean silent) throws NotFoundException, ClassNotFoundException, CannotCompileException {
        ClassPool cp = new ClassPool();
        cp.appendSystemPath();
        cp.appendClassPath(new LoaderClassPath(cl));

        CtClass shadowInterface = cp.getCtClass(ShadowInterface.class.getName());
        CtClass objectClass = cp.getCtClass(Object.class.getName());

        Set<String> annotated = scan(Shadow.class, cl, packageName);

        HashMap<CtClass, Class<?>> tempTargetMap = new HashMap<>();
        for (String name : annotated) {
            CtClass ctClass = cp.getCtClass(name);
            Shadow shadowAnnotation = (Shadow) ctClass.getAnnotation(Shadow.class);
            Class<?> shadowTarget = ShadowManager.indexToClass(shadowAnnotation.value());

            ArrayList<CtClass> interfaces = new ArrayList<>(Arrays.asList(ctClass.getInterfaces()));
            interfaces.add(shadowInterface);
            ctClass.setInterfaces(interfaces.toArray(new CtClass[0]));
            ctClass.addField(new CtField(cp.getCtClass(shadowTarget.getName()), "__realObject", ctClass));
            ctClass.addMethod(CtMethod.make("public Object getRealObject(){return this.__realObject;}", ctClass));

            shadowTargetMap.put(name, shadowTarget);
            tempTargetMap.put(ctClass, shadowTarget);
        }

        for (CtClass ctClass : tempTargetMap.keySet()) {
            boolean flag = true;
            for (CtConstructor constructor : ctClass.getConstructors()) {
                CtClass[] para = constructor.getParameterTypes();
                if (para.length == 1 && para[0] == objectClass) {
                    flag = false;
                    break;
                }
            }
            if (flag)
                ctClass.addConstructor(new CtConstructor(new CtClass[]{objectClass}, ctClass));
        }

        for (Map.Entry<CtClass, Class<?>> shadow : tempTargetMap.entrySet()) {
            CtClass ctClass = shadow.getKey();
            Class<?> shadowTarget = shadow.getValue();

            String defBody = ";";
            CtClass superClass = ctClass.getSuperclass();
            if (superClass != objectClass) {
                if (superClass.hasAnnotation(Shadow.class))
                    defBody = "super(null);";
                else
                    throw new IllegalArgumentException("Invalid superclass");
            }
            for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
                try {
                    constructor.setBody(defBody);

                    CtClass[] para = constructor.getParameterTypes();
                    if (para.length == 1 && para[0] == objectClass) {
                        if (Arrays.stream(shadowTarget.getConstructors()).anyMatch(c -> c.getParameterCount() == 1 &&
                                                                                        c.getParameterTypes()[0] ==
                                                                                        Object.class))
                            constructor.insertAfter("if($1 instanceof " +
                                                    shadowTarget.getName() +
                                                    ") $0.__realObject = (" +
                                                    shadowTarget.getName() +
                                                    ")$1; else $0.__realObject = new " +
                                                    shadowTarget.getName() +
                                                    "($1);", true);
                        else
                            constructor.insertAfter("if($1 instanceof " +
                                                    shadowTarget.getName() +
                                                    ") $0.__realObject = (" +
                                                    shadowTarget.getName() +
                                                    ")$1; else $0.__realObject = null;", true);
                    } else {
                        constructor.insertAfter("$0.__realObject = new " +
                                                shadowTarget.getName() +
                                                '(' +
                                                perfectForward(para) +
                                                ");", true);
                    }
                } catch (Throwable t) {
                    if (!silent)
                        t.printStackTrace();
                    constructor.setBody("throw new UnsupportedOperationException();");
                }
            }

            for (CtMethod method : ctClass.getDeclaredMethods()) {
                try {
                    String belong = Modifier.isStatic(method.getModifiers()) ?
                                    shadowTarget.getName() :
                                    "((" +
                                    shadowTarget.getName() +
                                    ")(((" +
                                    ShadowInterface.class.getName() +
                                    ")this).getRealObject()))";
                    if (method.hasAnnotation(Shadow.class)) {
                        Shadow shadowAnnotation = (Shadow) method.getAnnotation(Shadow.class);

                        CtClass[] para = method.getParameterTypes();
                        String methodName = ShadowManager.indexTo(shadowAnnotation.value());

                        CtClass retType = method.getReturnType();
                        if (retType == CtClass.voidType)
                            method.setBody(belong + '.' + methodName + '(' + perfectForward(para) + ");");
                        else if (shadowTargetMap.containsKey(retType.getName()))
                            method.setBody("return new " +
                                           retType.getName() +
                                           "((Object)(" +
                                           belong +
                                           '.' +
                                           methodName +
                                           '(' +
                                           perfectForward(para) +
                                           ")));");
                        else
                            method.setBody("return " + belong + '.' + methodName + '(' + perfectForward(para) + ");");
                    } else if (method.hasAnnotation(ShadowGetter.class)) {
                        ShadowGetter shadowAnnotation = (ShadowGetter) method.getAnnotation(ShadowGetter.class);

                        String fieldName = ShadowManager.indexTo(shadowAnnotation.value());
                        CtClass retType = method.getReturnType();
                        if (method.getParameterTypes().length != 0 ||
                            !shadowTarget.getDeclaredField(fieldName)
                                    .getType()
                                    .getName()
                                    .equals(shadowTargetMap.containsKey(retType.getName()) ?
                                            shadowTargetMap.get(retType.getName()).getName() :
                                            retType.getName()))
                            throw new IllegalArgumentException("Invalid getter");
                        if (shadowTargetMap.containsKey(retType.getName()))
                            method.setBody("return new " +
                                           retType.getName() +
                                           "((Object)(" +
                                           belong +
                                           '.' +
                                           fieldName +
                                           "));");
                        else
                            method.setBody("return " + belong + '.' + fieldName + ";");
                    } else if (method.hasAnnotation(ShadowSetter.class)) {
                        ShadowSetter shadowAnnotation = (ShadowSetter) method.getAnnotation(ShadowSetter.class);

                        CtClass[] para = method.getParameterTypes();
                        if (para.length != 1 || method.getReturnType() != CtClass.voidType)
                            throw new IllegalArgumentException("Invalid setter");
                        String fieldName = ShadowManager.indexTo(shadowAnnotation.value());
                        if (!shadowTarget.getDeclaredField(fieldName)
                                .getType()
                                .getName()
                                .equals(shadowTargetMap.containsKey(para[0].getName()) ?
                                        shadowTargetMap.get(para[0].getName()).getName() :
                                        para[0].getName()))
                            throw new IllegalArgumentException("Invalid setter");
                        if (shadowTargetMap.containsKey(para[0].getName()))
                            method.setBody(belong +
                                           '.' +
                                           fieldName +
                                           "=(" +
                                           shadowTargetMap.get(para[0].getName()).getName() +
                                           ")(((" +
                                           ShadowInterface.class.getName() +
                                           ")$1).getRealObject());");
                        else
                            method.setBody(belong + '.' + fieldName + "=$1;");
                    }
                } catch (Throwable t) {
                    if (!silent)
                        t.printStackTrace();
                    method.setBody("throw new UnsupportedOperationException();");
                }
            }
        }

        HashSet<CtClass> tempClass = new HashSet<>(tempTargetMap.keySet());
        while (!tempClass.isEmpty()) {
            CtClass cur = tempClass.iterator().next();
            while (cur.getSuperclass() != objectClass && tempClass.contains(cur.getSuperclass()))
                cur = cur.getSuperclass();
            definer.accept(cur);
            tempClass.remove(cur);
        }

        annotated = scan(ShadowExtend.class, cl, packageName);
        for (String name : annotated) {
            CtClass ctClass = cp.getCtClass(name);
            ShadowExtend shadowAnnotation = (ShadowExtend) ctClass.getAnnotation(ShadowExtend.class);
            Class<?> shadowTarget = ShadowManager.indexToClass(shadowAnnotation.value());
            ctClass.setSuperclass(cp.getCtClass(shadowTarget.getName()));

            for (CtConstructor constructor : ctClass.getDeclaredConstructors()) {
                try {
                    CtClass[] para = constructor.getParameterTypes();
                    MethodInfo methodInfo = constructor.getMethodInfo();
                    CodeAttribute codeAttribute = methodInfo.getCodeAttribute();
                    CodeIterator iterator = codeAttribute.iterator();
                    iterator.skipSuperConstructor();
                    byte[] orgCode = codeAttribute.getCode();
                    orgCode = Arrays.copyOfRange(orgCode, iterator.next(), orgCode.length);
                    constructor.setBody("super(" + perfectForward(para) + ");");
                    iterator = codeAttribute.iterator();
                    iterator.skipSuperConstructor();
                    iterator.appendGap(orgCode.length - (iterator.getCodeLength() - iterator.lookAhead()));
                    iterator.write(orgCode, iterator.next());
                    codeAttribute.computeMaxStack();
                    methodInfo.rebuildStackMap(cp);
                } catch (Throwable t) {
                    if (!silent)
                        t.printStackTrace();
                    constructor.setBody("throw new UnsupportedOperationException();");
                }
            }

            for (CtMethod method : ctClass.getDeclaredMethods()) {
                try {
                    String belong = Modifier.isStatic(method.getModifiers()) ? shadowTarget.getName() : "super";
                    if (method.hasAnnotation(Shadow.class)) {
                        CtClass[] para = method.getParameterTypes();
                        String methodName = ShadowManager.indexTo(((Shadow) method.getAnnotation(Shadow.class)).value());

                        CtClass retType = method.getReturnType();
                        if (retType == CtClass.voidType)
                            method.setBody(belong + '.' + methodName + '(' + perfectForward(para) + ");");
                        else if (shadowTargetMap.containsKey(retType.getName()))
                            method.setBody("return new " +
                                           retType.getName() +
                                           "((Object)(" +
                                           belong +
                                           '.' +
                                           methodName +
                                           '(' +
                                           perfectForward(para) +
                                           ")));");
                        else
                            method.setBody("return " + belong + '.' + methodName + '(' + perfectForward(para) + ");");
                    } else if (method.hasAnnotation(ShadowGetter.class)) {
                        String fieldName = ShadowManager.indexTo(((ShadowGetter) method.getAnnotation(ShadowGetter.class)).value());
                        CtClass retType = method.getReturnType();
                        if (method.getParameterTypes().length != 0 ||
                            !shadowTarget.getDeclaredField(fieldName)
                                    .getType()
                                    .getName()
                                    .equals(shadowTargetMap.containsKey(retType.getName()) ?
                                            shadowTargetMap.get(retType.getName()).getName() :
                                            retType.getName()))
                            throw new IllegalArgumentException("Invalid getter");
                        if (shadowTargetMap.containsKey(retType.getName()))
                            method.setBody("return new " +
                                           retType.getName() +
                                           "((Object)(" +
                                           belong +
                                           '.' +
                                           fieldName +
                                           "));");
                        else
                            method.setBody("return " + belong + '.' + fieldName + ";");
                    } else if (method.hasAnnotation(ShadowSetter.class)) {
                        CtClass[] para = method.getParameterTypes();
                        if (para.length != 1 || method.getReturnType() != CtClass.voidType)
                            throw new IllegalArgumentException("Invalid setter");
                        String fieldName = ShadowManager.indexTo(((ShadowSetter) method.getAnnotation(ShadowSetter.class)).value());
                        if (!shadowTarget.getDeclaredField(fieldName)
                                .getType()
                                .getName()
                                .equals(shadowTargetMap.containsKey(para[0].getName()) ?
                                        shadowTargetMap.get(para[0].getName()).getName() :
                                        para[0].getName()))
                            throw new IllegalArgumentException("Invalid setter");
                        if (shadowTargetMap.containsKey(para[0].getName()))
                            method.setBody(belong +
                                           '.' +
                                           fieldName +
                                           "=(" +
                                           shadowTargetMap.get(para[0].getName()).getName() +
                                           ")(((" +
                                           ShadowInterface.class.getName() +
                                           ")$1).getRealObject());");
                        else
                            method.setBody(belong + '.' + fieldName + "=$1;");
                    } else if (method.hasAnnotation(ShadowOverride.class)) {
                        String targetName = ShadowManager.indexTo(((ShadowOverride) method.getAnnotation(ShadowOverride.class)).value());
                        CtClass retType = method.getReturnType();
                        CtClass[] para = method.getParameterTypes();
                        if (method.getName().equals(targetName) && Arrays.stream(para).map(CtClass::getName).noneMatch(
                                shadowTargetMap::containsKey) && !shadowTargetMap.containsKey(retType.getName()))
                            continue;
                        CtMethod newMethod = new CtMethod(shadowTargetMap.containsKey(retType.getName()) ?
                                                          cp.getCtClass(shadowTargetMap.get(retType.getName())
                                                                                .getName()) :
                                                          retType, targetName + "__internal_override", Arrays.stream(
                                para).map(cc -> {
                            try {
                                return shadowTargetMap.containsKey(cc.getName()) ?
                                       cp.getCtClass(shadowTargetMap.get(cc.getName()).getName()) :
                                       cc;
                            } catch (NotFoundException e) {
                                throw new RuntimeException(e);
                            }
                        }).toArray(CtClass[]::new), ctClass);
                        if (retType == CtClass.voidType)
                            newMethod.setBody(method.getName() + '(' + perfectBackward(para) + ");");
                        else if (shadowTargetMap.containsKey(retType.getName()))
                            newMethod.setBody("return (" +
                                              shadowTargetMap.get(retType.getName()).getName() +
                                              ")(((" +
                                              ShadowInterface.class.getName() +
                                              ")(" +
                                              method.getName() +
                                              '(' +
                                              perfectBackward(para) +
                                              "))).getRealObject());");
                        else
                            newMethod.setBody("return " + method.getName() + '(' + perfectBackward(para) + ");");
                        newMethod.setName(targetName);
                        ctClass.addMethod(newMethod);
                    }
                } catch (Throwable t) {
                    if (!silent)
                        t.printStackTrace();
                    method.setBody("throw new UnsupportedOperationException();");
                }
            }
            definer.accept(ctClass);
        }
    }

    public static void initShadow(ClassLoader cl, String packageName, Consumer<CtClass> definer) throws NotFoundException, CannotCompileException, ClassNotFoundException {
        initShadow(cl, packageName, definer, false);
    }

    public static void initShadow(Class<?> neighbor, boolean silent) throws NotFoundException, CannotCompileException, ClassNotFoundException {
        initShadow(neighbor.getClassLoader(), neighbor.getPackage().getName(), cc -> {
            try {
                DefineClassHelper.toClass(cc.getName(), neighbor, neighbor.getClassLoader(), null, cc.toBytecode());
            } catch (Exception e) {
                e.printStackTrace();
            }
        }, silent);
    }

    public static void initShadow(Class<?> neighbor) throws NotFoundException, CannotCompileException, ClassNotFoundException {
        initShadow(neighbor, false);
    }

    /**
     * It doesn't work on Java 16 and higher.
     */
    @Deprecated
    public static void initShadow(ClassLoader cl, String packageName, boolean silent) throws NotFoundException, CannotCompileException, ClassNotFoundException {
        initShadow(cl, packageName, cc -> {
            try {
                cc.toClass(cl, null);
            } catch (CannotCompileException e) {
                e.printStackTrace();
            }
        }, silent);
    }

    /**
     * It doesn't work on Java 16 and higher.
     */
    @Deprecated
    public static void initShadow(ClassLoader cl, String packageName) throws NotFoundException, CannotCompileException, ClassNotFoundException {
        initShadow(cl, packageName, false);
    }
}
