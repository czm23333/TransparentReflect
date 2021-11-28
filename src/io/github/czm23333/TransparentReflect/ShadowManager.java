package io.github.czm23333.TransparentReflect;

import io.github.czm23333.TransparentReflect.annotations.Shadow;
import io.github.czm23333.TransparentReflect.annotations.ShadowExtend;
import io.github.czm23333.TransparentReflect.annotations.ShadowGetter;
import io.github.czm23333.TransparentReflect.annotations.ShadowOverride;
import io.github.czm23333.TransparentReflect.annotations.ShadowSetter;
import io.github.czm23333.TransparentReflect.internal.ShadowInterface;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtConstructor;
import javassist.CtField;
import javassist.CtMethod;
import javassist.LoaderClassPath;
import javassist.Modifier;
import javassist.bytecode.CodeAttribute;
import javassist.bytecode.CodeIterator;
import javassist.bytecode.MethodInfo;
import org.reflections.Reflections;
import org.reflections.scanners.Scanners;
import org.reflections.util.ConfigurationBuilder;

import java.lang.annotation.Annotation;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class ShadowManager {
    public static final Directory root = new Directory();

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

    private static Set<String> scan(Class<? extends Annotation> annotation, String pack) {
        Reflections reflections = new Reflections(new ConfigurationBuilder().forPackages(pack)
                                                          .setExpandSuperTypes(false));
        return reflections.get(Scanners.SubTypes.of(Scanners.TypesAnnotated.with(annotation)));
    }

    private static String perfectForward(HashMap<CtClass, Class<?>> tempTargetMap, CtClass[] para) {
        StringBuilder insertCode = new StringBuilder();
        for (int i = 1; i <= para.length; ++i) {
            CtClass paraClass = para[i - 1];
            if (tempTargetMap.containsKey(paraClass)) {
                insertCode.append('(');

                insertCode.append('(');
                insertCode.append(tempTargetMap.get(paraClass).getName());
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

    public static void initShadow(Class<?> neighbor) throws Throwable {
        ClassPool cp = new ClassPool();
        cp.appendClassPath(new LoaderClassPath(neighbor.getClassLoader()));

        CtClass shadowInterface = cp.getCtClass(ShadowInterface.class.getName());
        CtClass objectClass = cp.getCtClass(Object.class.getName());

        Set<String> annotated = scan(Shadow.class, neighbor.getPackageName());

        HashMap<CtClass, Class<?>> tempTargetMap = new HashMap<>();
        for (String name : annotated) {
            CtClass ctClass = cp.getCtClass(name);
            Shadow shadowAnnotation = (Shadow) ctClass.getAnnotation(Shadow.class);
            Class<?> shadowTarget = ShadowManager.indexToClass(shadowAnnotation.value());

            ArrayList<CtClass> interfaces = new ArrayList<>(List.of(ctClass.getInterfaces()));
            interfaces.add(shadowInterface);
            ctClass.setInterfaces(interfaces.toArray(new CtClass[0]));
            ctClass.addField(new CtField(cp.getCtClass(shadowTarget.getName()), "__realObject", ctClass));
            ctClass.addMethod(CtMethod.make("public Object getRealObject(){return this.__realObject;}", ctClass));

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
            for (CtConstructor constructor : ctClass.getConstructors()) {
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
                                            perfectForward(tempTargetMap, para) +
                                            ");", true);
                }
            }

            for (CtMethod method : ctClass.getMethods()) {
                if (method.getDeclaringClass() != ctClass)
                    continue;
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

                    if (method.getReturnType() == CtClass.voidType)
                        method.setBody(belong + '.' + methodName + '(' + perfectForward(tempTargetMap, para) + ");");
                    else
                        method.setBody("return " +
                                       belong +
                                       '.' +
                                       methodName +
                                       '(' +
                                       perfectForward(tempTargetMap, para) +
                                       ");");
                } else if (method.hasAnnotation(ShadowGetter.class)) {
                    ShadowGetter shadowAnnotation = (ShadowGetter) method.getAnnotation(ShadowGetter.class);

                    String fieldName = ShadowManager.indexTo(shadowAnnotation.value());
                    if (method.getParameterTypes().length != 0 ||
                        !shadowTarget.getDeclaredField(fieldName).getType().getName().equals(method.getReturnType()
                                                                                                     .getName()))
                        throw new IllegalArgumentException("Invalid getter");
                    method.setBody("return " + belong + '.' + fieldName + ";");
                } else if (method.hasAnnotation(ShadowSetter.class)) {
                    ShadowSetter shadowAnnotation = (ShadowSetter) method.getAnnotation(ShadowSetter.class);

                    CtClass[] para = method.getParameterTypes();
                    if (para.length != 1 || method.getReturnType() != CtClass.voidType)
                        throw new IllegalArgumentException("Invalid setter");
                    String fieldName = ShadowManager.indexTo(shadowAnnotation.value());
                    if (!shadowTarget.getDeclaredField(fieldName).getType().getName().equals(para[0].getName()))
                        throw new IllegalArgumentException("Invalid setter");
                    method.setBody(belong + '.' + fieldName + "=$1;");
                }
            }
        }

        HashSet<CtClass> tempClass = new HashSet<>(tempTargetMap.keySet());
        while (!tempClass.isEmpty()) {
            CtClass cur = tempClass.iterator().next();
            while (cur.getSuperclass() != objectClass && tempClass.contains(cur.getSuperclass()))
                cur = cur.getSuperclass();
            cur.toClass(neighbor);
            tempClass.remove(cur);
        }

        annotated = scan(ShadowExtend.class, neighbor.getPackageName());
        for (String name : annotated) {
            CtClass ctClass = cp.getCtClass(name);
            ShadowExtend shadowAnnotation = (ShadowExtend) ctClass.getAnnotation(ShadowExtend.class);
            Class<?> shadowTarget = ShadowManager.indexToClass(shadowAnnotation.value());
            ctClass.setSuperclass(cp.getCtClass(shadowTarget.getName()));

            for (CtConstructor constructor : ctClass.getConstructors()) {
                CtClass[] para = constructor.getParameterTypes();
                MethodInfo methodInfo = constructor.getMethodInfo();
                CodeAttribute codeAttribute = methodInfo.getCodeAttribute();
                CodeIterator iterator = codeAttribute.iterator();
                iterator.skipSuperConstructor();
                byte[] orgCode = codeAttribute.getCode();
                orgCode = Arrays.copyOfRange(orgCode, iterator.next(), orgCode.length);
                constructor.setBody("super(" + perfectForward(tempTargetMap, para) + ");");
                iterator = codeAttribute.iterator();
                iterator.skipSuperConstructor();
                iterator.appendGap(orgCode.length - (iterator.getCodeLength() - iterator.lookAhead()));
                iterator.write(orgCode, iterator.next());
                codeAttribute.computeMaxStack();
                methodInfo.rebuildStackMap(cp);
            }

            for (CtMethod method : ctClass.getMethods()) {
                if (method.getDeclaringClass() != ctClass)
                    continue;
                String belong = Modifier.isStatic(method.getModifiers()) ? shadowTarget.getName() : "super";
                if (method.hasAnnotation(Shadow.class)) {
                    CtClass[] para = method.getParameterTypes();
                    String methodName = ShadowManager.indexTo(((Shadow) method.getAnnotation(Shadow.class)).value());

                    if (method.getReturnType() == CtClass.voidType)
                        method.setBody(belong + '.' + methodName + '(' + perfectForward(tempTargetMap, para) + ");");
                    else
                        method.setBody("return " +
                                       belong +
                                       '.' +
                                       methodName +
                                       '(' +
                                       perfectForward(tempTargetMap, para) +
                                       ");");
                } else if (method.hasAnnotation(ShadowGetter.class)) {
                    String fieldName = ShadowManager.indexTo(((ShadowGetter) method.getAnnotation(ShadowGetter.class)).value());
                    if (method.getParameterTypes().length != 0 ||
                        !shadowTarget.getDeclaredField(fieldName).getType().getName().equals(method.getReturnType()
                                                                                                     .getName()))
                        throw new IllegalArgumentException("Invalid getter");
                    method.setBody("return " + belong + '.' + fieldName + ";");
                } else if (method.hasAnnotation(ShadowSetter.class)) {
                    CtClass[] para = method.getParameterTypes();
                    if (para.length != 1 || method.getReturnType() != CtClass.voidType)
                        throw new IllegalArgumentException("Invalid setter");
                    String fieldName = ShadowManager.indexTo(((ShadowSetter) method.getAnnotation(ShadowSetter.class)).value());
                    if (!shadowTarget.getDeclaredField(fieldName).getType().getName().equals(para[0].getName()))
                        throw new IllegalArgumentException("Invalid setter");
                    method.setBody(belong + '.' + fieldName + "=$1;");
                } else if (method.hasAnnotation(ShadowOverride.class))
                    method.setName(ShadowManager.indexTo(((ShadowOverride) method.getAnnotation(ShadowOverride.class)).value()));
            }
            ctClass.toClass(neighbor);
        }
    }
}
