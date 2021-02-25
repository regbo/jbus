package org.dizitart.jbus;

public interface Listener<X> {

	void accept(X event);
}
