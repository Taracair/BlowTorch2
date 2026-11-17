package org.keplerproject.luajava;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import java.lang.reflect.Method;

import org.junit.Test;

/**
 * A Lua proxy can stay registered after the state is closed. Returning null
 * from a primitive method makes the Java proxy throw, and {@code onPreDraw}
 * returning false cancels the frame.
 */
public class LuaProxyFallbackTest {

	private interface Sample {
		void onClick();
		boolean onPreDraw();
		boolean onTouch();
		int getCount();
		long getWhen();
		String getName();
	}

	private static Method method(String name, Class<?>... params) throws Exception {
		return Sample.class.getMethod(name, params);
	}

	@Test
	public void onPreDrawProceeds() throws Exception {
		assertEquals(Boolean.TRUE, LuaProxyFallback.valueFor(method("onPreDraw"), this, null));
	}

	@Test
	public void otherBooleansAreNotHandled() throws Exception {
		assertEquals(Boolean.FALSE, LuaProxyFallback.valueFor(method("onTouch"), this, null));
	}

	@Test
	public void voidAndReferenceAreNull() throws Exception {
		assertNull(LuaProxyFallback.valueFor(method("onClick"), this, null));
		assertNull(LuaProxyFallback.valueFor(method("getName"), this, null));
	}

	@Test
	public void numbersAreZero() throws Exception {
		assertEquals(Integer.valueOf(0), LuaProxyFallback.valueFor(method("getCount"), this, null));
		assertEquals(Long.valueOf(0L), LuaProxyFallback.valueFor(method("getWhen"), this, null));
	}

	@Test
	public void objectMethodsStayUsable() throws Exception {
		Object proxy = new Object();
		Method hash = Object.class.getMethod("hashCode");
		Method eq = Object.class.getMethod("equals", Object.class);
		Method str = Object.class.getMethod("toString");
		assertEquals(Integer.valueOf(System.identityHashCode(proxy)),
				LuaProxyFallback.valueFor(hash, proxy, null));
		assertEquals(Boolean.TRUE, LuaProxyFallback.valueFor(eq, proxy, new Object[] { proxy }));
		assertEquals(Boolean.FALSE, LuaProxyFallback.valueFor(eq, proxy, new Object[] { this }));
		assertEquals("LuaProxy(closed)", LuaProxyFallback.valueFor(str, proxy, null));
		assertEquals(Boolean.FALSE, LuaProxyFallback.valueFor(eq, proxy, null));
	}
}
