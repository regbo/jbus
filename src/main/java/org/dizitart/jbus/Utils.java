package org.dizitart.jbus;

class Utils {

	public static <X> X requireNonNull(X value, String... messages) {
		if (value == null) {
			String exMessage = null;
			if (messages != null)
				for (String message : messages) {
					if (message == null || message.length() == 0)
						continue;
					if (exMessage == null)
						exMessage = "";
					else
						exMessage += " ";
					exMessage += message;
				}
			if (exMessage != null)
				throw new NullPointerException(exMessage);
			else
				throw new NullPointerException();
		}
		return value;
	}

}
