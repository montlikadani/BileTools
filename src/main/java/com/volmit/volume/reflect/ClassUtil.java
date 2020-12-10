package com.volmit.volume.reflect;

import com.volmit.volume.lang.collections.GMap;

/**
 * Class utilities (object boxing)
 *
 * @author cyberpwn
 */
@SuppressWarnings("serial")
public class ClassUtil {
	public static GMap<Class<?>, Class<?>> boxes;
	public static GMap<Class<?>, Class<?>> unboxes;

	/**
	 * To wrapper from primative or the same class returned
	 *
	 * @param c the class
	 * @return the wrapper class or the same class
	 */
	public static Class<?> toWrapper(Class<?> c) {
		return isPrimative(c) ? boxes.get(c) : c;
	}

	/**
	 * To primative from wrapper or the same class returned
	 *
	 * @param c the class
	 * @return the primative class or the same class
	 */
	public static Class<?> toPrimative(Class<?> c) {
		return isWrapper(c) ? unboxes.get(c) : c;
	}

	/**
	 * Check if this class is involved with primative boxing
	 *
	 * @param c the class
	 * @return true if its a wrapped primative or a primative
	 */
	public static boolean isWrapperOrPrimative(Class<?> c) {
		return isPrimative(c) || isWrapper(c);
	}

	/**
	 * Check if this class is a primative type
	 *
	 * @param c the class
	 * @return true if it is
	 */
	public static boolean isPrimative(Class<?> c) {
		return boxes.containsKey(c);
	}

	/**
	 * Check if this class is a wrapper of a primative
	 *
	 * @param c the class
	 * @return true if it is
	 */
	public static boolean isWrapper(Class<?> c) {
		return boxes.containsValue(c);
	}

	static {
		boxes = new GMap<Class<?>, Class<?>>() {
			{
				put(int.class, Integer.class);
				put(long.class, Long.class);
				put(short.class, Short.class);
				put(char.class, Character.class);
				put(byte.class, Byte.class);
				put(boolean.class, Boolean.class);
				put(float.class, Float.class);
			}
		};

		unboxes = new GMap<Class<?>, Class<?>>() {
			{
				put(Integer.class, int.class);
				put(Long.class, long.class);
				put(Short.class, short.class);
				put(Character.class, char.class);
				put(Byte.class, byte.class);
				put(Boolean.class, boolean.class);
				put(Float.class, float.class);
			}
		};
	}

	public static Class<?> flip(Class<?> c) {
		if (isPrimative(c)) {
			return toWrapper(c);
		}

		if (isWrapper(c)) {
			return toPrimative(c);
		}

		return c;
	}
}
