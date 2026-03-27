package top.niunaijun.blackbox.utils;

import java.util.Arrays;
import java.util.Objects;

public class BzArrayUtils {

	public static <T> T[] trimToSize(T[] array, int size) {
		if (array == null || size <= 0) {
			return null;
		}
		if (array.length == size) {
			return array;
		}
		return Arrays.copyOf(array, size);
	}

	public static Object[] push(Object[] array, Object item) {
		if (array == null) {
			return new Object[]{item};
		}
		Object[] out = Arrays.copyOf(array, array.length + 1);
		out[array.length] = item;
		return out;
	}

	public static <T> boolean contains(T[] array, T value) {
		return indexOf(array, value) >= 0;
	}

	public static boolean contains(int[] array, int value) {
		if (array == null) {
			return false;
		}
		for (int element : array) {
			if (element == value) {
				return true;
			}
		}
		return false;
	}

	public static <T> int indexOf(T[] array, T value) {
		if (array == null) {
			return -1;
		}
		for (int i = 0; i < array.length; i++) {
			if (Objects.equals(array[i], value)) {
				return i;
			}
		}
		return -1;
	}

	public static int protoIndexOf(Class<?>[] array, Class<?> type) {
		if (array == null) {
			return -1;
		}
		for (int i = 0; i < array.length; i++) {
			if (array[i] == type) {
				return i;
			}
		}
		return -1;
	}

	public static int indexOfFirst(Object[] array, Class<?> type) {
		if (isEmpty(array)) {
			return -1;
		}
		for (int i = 0; i < array.length; i++) {
			Object candidate = array[i];
			if (candidate != null && candidate.getClass() == type) {
				return i;
			}
		}
		return -1;
	}

	public static int protoIndexOf(Class<?>[] array, Class<?> type, int sequence) {
		if (array == null || sequence < 0) {
			return -1;
		}
		for (int i = sequence; i < array.length; i++) {
			if (array[i] == type) {
				return i;
			}
		}
		return -1;
	}

	public static int indexOfObject(Object[] array, Class<?> type, int sequence) {
		if (array == null || sequence < 0) {
			return -1;
		}
		for (int i = sequence; i < array.length; i++) {
			if (type.isInstance(array[i])) {
				return i;
			}
		}
		return -1;
	}

	public static int indexOf(Object[] array, Class<?> type, int sequence) {
		if (isEmpty(array) || sequence <= 0) {
			return -1;
		}
		int matchCount = 0;
		for (int i = 0; i < array.length; i++) {
			Object candidate = array[i];
			if (candidate != null && candidate.getClass() == type) {
				matchCount++;
				if (matchCount == sequence) {
					return i;
				}
			}
		}
		return -1;
	}

	public static int indexOfLast(Object[] array, Class<?> type) {
		if (isEmpty(array)) {
			return -1;
		}
		for (int i = array.length - 1; i >= 0; i--) {
			Object candidate = array[i];
			if (candidate != null && candidate.getClass() == type) {
				return i;
			}
		}
		return -1;
	}

	public static <T> boolean isEmpty(T[] array) {
		return array == null || array.length == 0;
	}

	@SuppressWarnings("unchecked")
	public static <T> T getFirst(Object[] args, Class<?> clazz) {
		int index = indexOfFirst(args, clazz);
		return index < 0 ? null : (T) args[index];
	}

	public static void checkOffsetAndCount(int arrayLength, int offset, int count) throws ArrayIndexOutOfBoundsException {
		if ((offset | count) < 0 || offset > arrayLength || arrayLength - offset < count) {
			throw new ArrayIndexOutOfBoundsException(offset);
		}
	}
}
