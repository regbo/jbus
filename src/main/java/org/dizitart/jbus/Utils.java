package org.dizitart.jbus;

class Utils {

	public static <X> X requireNonNull(X value) {
		if (value == null)
			throw new NullPointerException();
		return value;
	}

}
