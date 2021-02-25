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
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * A class to hold the records of listeners registered to the event bus runtime.
 *
 * @since 1.0
 * @author Anindya Chatterjee.
 */
class ListenersRegistry<T> {
	private static final Class<?> THIS_CLASS = new Object() {
	}.getClass().getEnclosingClass();
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(THIS_CLASS);

	// keep track of event and its registered subscribed methods
	private Map<Class<?>, List<ListenerMethod>> registry = new ConcurrentHashMap<Class<?>, List<ListenerMethod>>();
	// cache to keep track of all strong referenced listener object
	private final List<Object> subscriberCache = new CopyOnWriteArrayList<Object>();
	// cache to keep track of all weak referenced listener object
	private final List<WeakReference<Object>> weakSubscriberCache = new CopyOnWriteArrayList<WeakReference<Object>>();
	private final Object lock = new Object();
	private final Class<T> eventType;

	public ListenersRegistry(Class<T> eventType) {
		this.eventType = Utils.requireNonNull(eventType);
	}

	/**
	 * Registers an object in the listener registry. If weak is set, it will create
	 * a weak reference of the listener object and register. Otherwise it will store
	 * a strong reference of the listener object.
	 * 
	 * @param forceSyncState
	 *
	 */
	void register(Object listener, boolean weak, boolean forceAsync) {
		WeakReference<Object> weakListener = null;

		// synchronize the search in the cache, to check if the listener
		// has already been registered.
		synchronized (lock) {
			if (weak) {
				// if the weak is set, check if a weak reference of the object is
				// kept in the cache or not.
				if (containsWeak(listener))
					throw new JBusException(listener + " has already been registered.");
				// create a weak reference of the object and add it to the cache
				weakListener = new WeakReference<Object>(listener);
				weakSubscriberCache.add(weakListener);
				logger.trace("{} added to the weak subscriber cache.", listener);
			} else {
				if (subscriberCache.contains(listener))
					// if listener is found in the strong referenced cache, throw
					throw new JBusException(listener + " has already been registered.");
				// add the object to the strong referenced cache
				subscriberCache.add(listener);
				logger.trace("{} added to the subscriber cache.", listener);
			}
		}
		// extract all subscribed methods from the listener and its super class and
		// interfaces.
		// up to this point, we hold a strong reference of the object, beyond this
		// point,
		// if the weak is set, we will not hold any strong reference of the object.
		List<ListenerMethod> subscribedMethods = ReflectionUtil.findSubscribeMethods(eventType, listener, forceAsync);
		if (subscribedMethods == null || subscribedMethods.isEmpty())
			throw new JBusException(listener + " does not have any method marked with @Subscribe.");

		for (ListenerMethod listenerMethod : subscribedMethods) {
			if (weak) {
				listenerMethod.weakListener = weakListener;
				listenerMethod.holdWeakReference = true;
			} else {
				listenerMethod.target = listener;
				listenerMethod.holdWeakReference = false;
			}

			Class<?> eventType = listenerMethod.eventType;
			if (registry.containsKey(eventType)) {
				List<ListenerMethod> listenerMethods = registry.get(eventType);

				// check ListenerMethod's equals method
				if (!listenerMethods.contains(listenerMethod)) {
					listenerMethods.add(listenerMethod);
					logger.trace("{} has been registered.", listenerMethod);
				} else {
					logger.trace("{} has already been registered.", listenerMethod);
				}
			} else {
				List<ListenerMethod> listenerMethods = new CopyOnWriteArrayList<ListenerMethod>();
				listenerMethods.add(listenerMethod);
				registry.put(listenerMethod.eventType, listenerMethods);
				logger.trace(listenerMethod + " has been registered.");
			}
		}
	}

	/**
	 * De-registers a listener object.
	 */
	void deregister(Object listener) {
		// synchronize the search. search and remove are put in one place
		// to avoid unnecessary looping.
		synchronized (lock) {
			// we need to check in both the caches, as we don't know how
			// the listener object was registered. If it was a weak reference,
			// there are chances, that underlying object has already been
			// collected by GC. In that case while iterating we will cleanup
			// the cache as we go, if we find such weak reference.
			//
			// But one catch here is that we will never know if we are trying
			// to deregister an object which was never registered before, hence
			// we can not throw such exception.

			for (WeakReference<Object> weakRef : weakSubscriberCache) {
				Object element = weakRef.get();
				if (element == null) {
					// underlying object is no more. remove it from the cache already.
					if (weakSubscriberCache.remove(weakRef)) {
						logger.trace("{} removed from cache as underlying object does not exist anymore.", weakRef);
					}
				} else {
					if (element.equals(listener)) {
						// found in weak cache, remove it and break
						if (weakSubscriberCache.remove(weakRef)) {
							logger.trace("{} removed from the weak subscriber reference cache.", listener);
						}
						break;
					}
				}
			}

			for (Object object : subscriberCache) {
				if (object.equals(listener)) {
					// found in strong cache, remove it break
					if (subscriberCache.remove(listener)) {
						logger.trace("{} removed from the subscriber cache.", listener);
					}
					break;
				}
			}
		}

		// remove the listener and its subscribed methods from the registry,
		// we are not 100% sure if we are dealing with weak referenced object
		// or not, hence we are passing false.
		removeFromRegistry(listener, false);
	}

	/**
	 * Get all registered subscriber information for an event.
	 */
	List<ListenerMethod> getSubscribers(Object event) {
		if (event == null)
			return Collections.emptyList();
		Class<?> eventType = event.getClass();
		// loop through the registry to get all subscribed method
		List<ListenerMethod> results = new ArrayList<ListenerMethod>();
		for (Entry<Class<?>, List<ListenerMethod>> ent : registry.entrySet()) {
			if (ent.getKey().isAssignableFrom(event.getClass()))
				results.addAll(ent.getValue());
		}
		return Collections.unmodifiableList(results);

	}

	/**
	 * Checks if an object's weak reference is kept in the cache or not.
	 */
	private boolean containsWeak(Object listener) {
		for (WeakReference<Object> weakRef : weakSubscriberCache) {
			Object element = weakRef.get();
			if (element == null) {
				// if the object has already been claimed by the GC,
				// remove it from the cache.
				if (weakSubscriberCache.remove(weakRef)) {
					logger.trace("{} removed from cache as underlying object does not exist anymore.", weakRef);
				}
			} else {
				if (element.equals(listener)) {
					// if the listener is found equal to any weak referenced object,
					// return true immediately.
					return true;
				}
			}
		}
		return false;
	}

	/**
	 * Removes a weak referenced listener from the bus runtime.
	 */
	void removeWeakListener(WeakReference<?> weakRef) {
		// first remove it from cache
		if (weakSubscriberCache.remove(weakRef))
			logger.trace("Weak reference {} removed from cache.", weakRef);

		// clean up the registry. Most of the time removeWeakListener is called when
		// underlying object is garbage collected and we want to remove the weak
		// reference from the runtime. So weakRef.get() will always return null
		// in that case.
		//
		// But fortunately, removeFromRegistry method will cleanup the cache while
		// iterating if it finds a weak reference which does not hold any object
		// reference any more.
		removeFromRegistry(weakRef.get(), true);
	}

	/**
	 * Removes the listener from the registry. It also cleans up the registry, while
	 * iterating, if it finds any weak reference which does not hold any object
	 * reference any more, i.e. the underlying object has been garbage collected.
	 *
	 * @param confirmedWeak if we know listener was registered as a weak reference
	 */
	private void removeFromRegistry(Object listener, boolean confirmedWeak) {
		// iterate the whole registry map
		for (Map.Entry<Class<?>, List<ListenerMethod>> entry : registry.entrySet()) {
			List<ListenerMethod> subscribedMethods = entry.getValue();
			for (ListenerMethod listenerMethod : subscribedMethods) {
				if (confirmedWeak || listenerMethod.holdWeakReference) {
					// if confirmedWeak is true or listener method holds weak reference,
					// check if underlying object is still valid.

					// if not valid clean up. remove the entry from cache and
					// from the event's subscriber list.
					Object reference = listenerMethod.weakListener.get();
					if (reference == null) {
						// remove from event subscribers list
						if (subscribedMethods.remove(listenerMethod)) {
							logger.trace("{} has been un-registered as the target has been garbage collected.",
									listenerMethod);
						}
						// remove that invalid weak reference from cache
						if (weakSubscriberCache.remove(listenerMethod.weakListener)) {
							logger.trace("{} removed from cache as underlying object does not exist anymore.",
									listenerMethod.weakListener);
						}
					} else if (reference.equals(listener)) {
						if (subscribedMethods.remove(listenerMethod)) {
							logger.trace("{} has been un-registered.", listenerMethod);
						}
					}
				} else {
					if (listenerMethod.target.equals(listener)) {
						if (subscribedMethods.remove(listenerMethod)) {
							logger.trace("{} has been un-registered.", listenerMethod);
						}
					}
				}
			}
		}
	}
}
