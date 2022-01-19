package io.github.czm23333.transparentreflect.internal;

import io.github.czm23333.transparentreflect.ShadowManager;

/**
 * For internal use only. To get the real object, use {@link ShadowManager#shadowUnpack(Object)} instead.
 */
public interface ShadowInterface {
    Object getRealObject();
}
