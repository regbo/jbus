/*
 * Copyright (c) 2016 JBus author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.dizitart.jbus;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * A reflection utility class to extract information about subscriber methods
 * from a registering listener object.
 *
 * @since 1.0
 * @author Anindya Chatterjee.
 */
class ReflectionUtil {

	private static final Method Listener_accept_METHOD;
	static {
		try {
			Listener_accept_METHOD = Listener.class.getDeclaredMethod("accept", Object.class);
		} catch (NoSuchMethodException e) {
			throw new RuntimeException(e);
		}
	}

	static List<ListenerMethod> findSubscribeMethods(Class<?> requireEventType, Object listener, boolean forceAsync) {
		if (listener == null)
			return Collections.emptyList();
		return findSubscribeMethods(requireEventType, listener.getClass(), forceAsync, new HashSet<Class<?>>());
	}

	/**
	 * Finds all subscriber methods in the whole class hierarchy of
	 * {@code subscribedClass}.
	 * 
	 * @param forceAsync
	 *
	 */
	private static List<ListenerMethod> findSubscribeMethods(Class<?> requireEventType, Class<?> subscribedClass,
			boolean forceAsync, Set<Class<?>> classVisitTracker) {
		if (subscribedClass == null)
			return Collections.emptyList();
		if (Object.class.equals(subscribedClass))
			return Collections.emptyList();
		if (!classVisitTracker.add(subscribedClass))
			return Collections.emptyList();
		Set<ListenerMethod> listenerMethods = new LinkedHashSet<ListenerMethod>();
		Method[] declaredMethods = subscribedClass.getDeclaredMethods();
		for (Method method : declaredMethods) {
			if (!isInvokableMethod(method))
				continue;
			Boolean subscribeAsync = getSubscribeAsync(subscribedClass, method);
			if (subscribeAsync == null)
				continue;
			Class<?>[] parameterTypes = method.getParameterTypes();
			if (parameterTypes == null || parameterTypes.length != 1)
				throw new JBusException(method + " is subscribe enabled, but it should have exactly 1 parameter.");
			Class<?> eventType = parameterTypes[0];
			if (!requireEventType.isAssignableFrom(eventType)) {
				if (Listener.class.isAssignableFrom(subscribedClass))
					eventType = requireEventType;
				else
					return null;
			}
			if (eventType.isArray() || method.isVarArgs())
				throw new JBusException(
						method + " is subscribe enabled, " + "but its parameter should not be an array or varargs.");
			ListenerMethod listenerMethod = ListenerMethod.create(method, eventType, forceAsync || subscribeAsync);
			listenerMethods.add(listenerMethod);
		}
		if (subscribedClass.getSuperclass() != null) {
			List<ListenerMethod> subscribedMethods = findSubscribeMethods(requireEventType,
					subscribedClass.getSuperclass(), forceAsync, classVisitTracker);
			listenerMethods.addAll(subscribedMethods);
		}
		if (subscribedClass.getInterfaces() != null) {
			for (Class<?> interfaceClass : subscribedClass.getInterfaces()) {
				List<ListenerMethod> subscribedMethods = findSubscribeMethods(requireEventType, interfaceClass,
						forceAsync, classVisitTracker);
				listenerMethods.addAll(subscribedMethods);
			}
		}
		return Collections.unmodifiableList(new ArrayList<ListenerMethod>(listenerMethods));
	}

	private static Boolean getSubscribeAsync(Class<?> subscribedClass, Method method) {
		Subscribe subscribe = null;
		if (method.isAnnotationPresent(Subscribe.class))
			subscribe = method.getAnnotation(Subscribe.class);
		if (subscribe != null)
			return subscribe.async();
		if (Listener.class.isAssignableFrom(subscribedClass) && Listener_accept_METHOD.equals(method))
			return false;
		return null;
	}

	private static boolean isInvokableMethod(Method method) {
		if (!method.isBridge() && !method.isSynthetic() && method.getParameterTypes().length == 1)
			return true;
		return false;
	}

}
