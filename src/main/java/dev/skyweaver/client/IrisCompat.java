package dev.skyweaver.client;

import dev.skyweaver.Skyweaver;
import java.lang.reflect.Method;
import org.jetbrains.annotations.Nullable;

/**
 * Soft, reflection-only Iris integration. Skyweaver has no Iris dependency, so it keeps working
 * whether Iris is absent, present without shaders, or present with a shader pack.
 */
final class IrisCompat {
    private static boolean resolved;
    private static @Nullable Method getInstance;
    private static @Nullable Method isShaderPackInUse;

    static boolean isShaderPackInUse() {
        if (!resolved) {
            resolved = true;
            try {
                Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
                getInstance = api.getMethod("getInstance");
                isShaderPackInUse = api.getMethod("isShaderPackInUse");
            } catch (Throwable ignored) {
                getInstance = null;
                isShaderPackInUse = null;
            }
        }

        if (getInstance == null || isShaderPackInUse == null) {
            return false;
        }

        try {
            Object instance = getInstance.invoke(null);
            Object result = isShaderPackInUse.invoke(instance);
            return Boolean.TRUE.equals(result);
        } catch (Throwable t) {
            Skyweaver.LOGGER.debug("Iris shader state could not be read; assuming no shader pack.", t);
            getInstance = null;
            isShaderPackInUse = null;
            return false;
        }
    }

    private IrisCompat() {
    }
}
