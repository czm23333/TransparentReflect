# TransparentReflect
A Lightweight Transparent Reflection Framework

## What can I do with this framework?
With this framework, you can create shadow classes and link them with actual classes at runtime. And then these shadow classes will behavior as if they were corresponding actual classes.

That is, you can achieve what you need reflection to do before in a completely transparent way.

Specifically, this framework supports: 

1. Use shadow class to create and access classes and objects transparently.

2. Use shadow class to extend certain classes to override methods declared in them and access their protected members transparently.

## Usage
You can find code examples in src/io/github/czm23333/TransparentReflect/example.

### Creating And Accessing Classes And Objects
#### Class Defination
To achieve this goal, you need to create a shadow class with a @Shadow annotation describing the actual class you want.

Like this: 

```java
@Shadow("Target")
public class ShadowTarget {}
```

Then you can add shadow methods using @ShadowMethod, shadow getters using @ShadowGetter, shadow setters using @ShadowSetter and shadow constructors in no need of annotations. These annotations are used just like @Shadow.

Shadow classes can extend each other if their actual classes also match those relationships.

Note that shadow methods, getters, setters and constructors must have correct signatures. That is, their return type and parameters must be equal to those of actual methods. (however there are some exceptions: each of those types can be the shadow type of the actual one and they'll be automatically transformed at runtime)

#### Access Existing Objects
No matter whether you've defined a shadow constructor with only one *Object* parameter or not, there'll be one at runtime.

This constructor has a special function: when you pass an instance of the actual class, it'll create a shadow object used to access this existing instance.

### Overriding Methods And Accessing Protected Members
You need to create a shadow class with a @ShadowExtend annotation instead of a @Shadow annotation.

Then declare shadow override methods with @ShadowOverride annotations to override methods declared in the actual class.

You can still use @ShadowMethod, @ShadowGetter and @ShadowSetter. They can access protected members now.

### Static Routes
*To be continued.*

### Link Shadow Classes With Actual Classes
Before you use any shadow classes, call ShadowManager.initShadow.

You can refer to code examples to see how to use it.

After this step you are able to use your shadow classes as if you were using the actual classes.
