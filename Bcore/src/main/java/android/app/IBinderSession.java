package android.app;

import android.os.IInterface;

/**
 * Compile-time stub for newer Android versions where {@code android.app.IServiceConnection}
 * adds an {@code IBinderSession} parameter.
 * <p>
 * At runtime the real framework class from the boot classpath is used.
 */
public interface IBinderSession extends IInterface {
}
