package org.keplerproject.luajava;

import java.lang.reflect.Method;

/**
 * What a Lua proxy returns after {@link LuaState#close()}. The proxy object
 * can still be registered with Android (for example {@code OnPreDrawListener}).
 */
final class LuaProxyFallback {
	private LuaProxyFallback() {}

	static Object valueFor(Method method, Object proxy, Object[] args) {
		if (method == null) {
			return null;
		}
		Class<?>[] params = method.getParameterTypes();
		String name = method.getName();
		if ("hashCode".equals(name) && params.length == 0) {
			return Integer.valueOf(System.identityHashCode(proxy));
		}
		if ("equals".equals(name) && params.length == 1) {
			Object other = (args != null && args.length > 0) ? args[0] : null;
			return Boolean.valueOf(proxy == other);
		}
		if ("toString".equals(name) && params.length == 0) {
			return "LuaProxy(closed)";
		}
		return zero(method.getReturnType(), name);
	}

	private static Object zero(Class<?> type, String name) {
		if (type == boolean.class || type == Boolean.class) {
			// OnPreDrawListener: false cancels the drawing pass.
			if ("onPreDraw".equals(name)) {
				return Boolean.TRUE;
			}
			return Boolean.FALSE;
		}
		if (type == int.class || type == Integer.class) {
			return Integer.valueOf(0);
		}
		if (type == long.class || type == Long.class) {
			return Long.valueOf(0L);
		}
		if (type == float.class || type == Float.class) {
			return Float.valueOf(0f);
		}
		if (type == double.class || type == Double.class) {
			return Double.valueOf(0d);
		}
		if (type == byte.class || type == Byte.class) {
			return Byte.valueOf((byte) 0);
		}
		if (type == short.class || type == Short.class) {
			return Short.valueOf((short) 0);
		}
		if (type == char.class || type == Character.class) {
			return Character.valueOf('\0');
		}
		return null;
	}
}
