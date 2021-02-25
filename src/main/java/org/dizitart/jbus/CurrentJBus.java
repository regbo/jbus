package org.dizitart.jbus;

import java.util.Map;
import java.util.AbstractMap.SimpleEntry;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

public enum CurrentJBus {
	INSTANCE;

	private final Map<Thread, Entry<JBus<?>, Object>> TRACKING = new ConcurrentHashMap<Thread, Entry<JBus<?>, Object>>();

	void setCurrent(JBus<?> jBus, Object listener) {
		if (jBus == null)
			TRACKING.remove(Thread.currentThread());
		else
			TRACKING.put(Thread.currentThread(), new SimpleEntry<JBus<?>, Object>(jBus, listener));
	}

	Entry<JBus<?>, Object> getCurrent() {
		return TRACKING.get(Thread.currentThread());
	}

}
