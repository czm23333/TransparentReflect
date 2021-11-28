package io.github.czm23333.TransparentReflect.internal;

import io.github.czm23333.TransparentReflect.ShadowManager;

/**
 * For internal use only. To get the real object, use {@link ShadowManager#shadowUnpack(Object)} instead.
 */
public interface ShadowInterface {
    Object getRealObject();
}
