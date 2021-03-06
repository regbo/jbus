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

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;

/**
 * A class to hold all information of a subscriber method.
 *
 * @since 1.0
 * @author Anindya Chatterjee.
 */
class ListenerMethod {

	public static ListenerMethod create(Method method, Class<?> eventType, boolean async) {
		Utils.requireNonNull(method);
		Utils.requireNonNull(eventType);
		if (!Modifier.isPublic(method.getModifiers()))
			method.setAccessible(true);
		ListenerMethod lm = new ListenerMethod(method, eventType);
		lm.async = async;
		return lm;
	}

	Object target;
	WeakReference<?> weakListener;

	Method method;
	Class<?> eventType;

	boolean async;
	boolean holdWeakReference;

	private ListenerMethod(Method method, Class<?> eventType) {
		this.method = method;
		this.eventType = eventType;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj)
			return true;
		if (obj == null)
			return false;
		if (getClass() != obj.getClass())
			return false;
		ListenerMethod other = (ListenerMethod) obj;

		/*
		 * Equality condition:
		 *
		 * 1. Method name should be equal. 2. Method are not overridden. Overridden
		 * methods are ignored. It will only consider the method from the first child
		 * class. Overridden methods from super class are considered equal. private
		 * method can not be overridden, so it is considered as different method.
		 * private methods with same name are allowed in class hierarchy here. 3. Method
		 * should have same argument type. 4. Target class is a super class of the
		 * other. 6. Target points to the same object. 5. Listener should have same
		 * execution mode (sync/async)
		 */

		if (holdWeakReference && other.holdWeakReference) {
			Object t1 = weakListener.get();
			Object t2 = other.weakListener.get();

			if ((t1 == null && t2 == null) || (t1 != null && t2 != null)) {
				if (method.getName().equals(other.method.getName()) && method.getModifiers() != Modifier.PRIVATE
						&& other.method.getModifiers() != Modifier.PRIVATE && eventType.equals(other.eventType)
						&& async == other.async) {
					return t1 == null || (t1.equals(t2) && (t1.getClass().isAssignableFrom(t2.getClass())
							|| t2.getClass().isAssignableFrom(t1.getClass())));
				}
			}
		} else {
			return other.method.getName().equals(method.getName()) && other.method.getModifiers() != Modifier.PRIVATE
					&& method.getModifiers() != Modifier.PRIVATE && eventType.equals(other.eventType)
					&& async == other.async && target != null && other.target != null && target.equals(other.target)
					&& (target.getClass().isAssignableFrom(other.target.getClass())
							|| other.target.getClass().isAssignableFrom(target.getClass()));
		}
		return false;
	}

	@Override
	public int hashCode() {

		final int prime = 31;
		int result = 1;
		result = prime * result + (holdWeakReference ? 1231 : 1237);
		result = prime * result + (async ? 1231 : 1237);
		result = prime * result + ((method == null) ? 0 : method.hashCode());
		if (holdWeakReference)
			result = prime * result + ((weakListener == null) ? 0 : weakListener.hashCode());
		else
			result = prime * result + ((target == null) ? 0 : target.hashCode());
		result = prime * result + ((eventType == null) ? 0 : eventType.hashCode());
		return result;

	}

	@Override
	public String toString() {
		return "[" + "method = " + method.getName() + ", async = " + async + ", weak = " + holdWeakReference
				+ ", target = " + (holdWeakReference ? weakListener.get() : target) + ", event = " + eventType.getName()
				+ "]";
	}
}
