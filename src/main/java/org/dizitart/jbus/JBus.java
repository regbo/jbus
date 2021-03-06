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

import java.util.AbstractMap.SimpleEntry;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * An event bus for java 1.6+. It dispatches event to registered listeners.
 *
 * <p>
 * It is a simple but powerful publish-subscribe event system. It requires
 * object to register themselves with the event bus to receive events. This
 * event bus is safe for concurrent use.
 * </p>
 *
 * <p>
 * <b>To receive events</b> from event bus, an object must:
 * <ol>
 * <li>register itself with the event bus via {@link #register(Object)} or
 * {@link #registerWeak(Object)} method</li>
 * <li>have at least one method marked with {@link Subscribe} annotation in its
 * class hierarchy</li>
 * <li>subscribed method should accept <b>only one parameter</b> having the type
 * of the event</li>
 * </ol>
 *
 *
 * {@link #register(Object)} will scan the input object for any method which has
 * been marked with {@link Subscribe} annotation and it will keep track of all
 * such methods found. If the subscribing method has more than one parameter,
 * runtime will throw a {@link JBusException} during registration. A subscriber
 * method can have any access modifier. {@link #register(Object)} will scan
 * through full class hierarchy of the input object including any super class
 * and interfaces.
 *
 * <p>
 * Upon successful registration, the runtime will keep track of all subscriber
 * methods found along with a strong reference of the input object for future
 * invocation. To store a weak reference of the input object instead of a strong
 * one, use the {@link #registerWeak(Object)} variant.
 * </p>
 *
 * A developer must {@link #deregister(Object)} the object to stop receiving
 * events. The behavior of {@link #deregister(Object)} is not deterministic in
 * the case of <em>weak registration</em> of the object. As the runtime
 * automatically cleans up any invalid weak references and any subscriber
 * methods associated with it as it goes, so a call to
 * {@link #deregister(Object)} might not do anything if the object has already
 * been garbage collected and the event bus runtime has cleared up its records
 * of subscriber methods already.
 *
 *
 * <p>
 * <b>To post an event</b> to the event bus, simply call {@link #post(Object)}
 * passing the event object. Event bus will automatically route the event
 * depending on its type to a handler chain. Handler chain is a collection of
 * registered subscribers of the event. By design, event bus does not support
 * inheritance for the event object.
 * </p>
 *
 * If an event implements {@link HandlerChainAware} then before each invocation,
 * the runtime will check if an interruption has been signalled from the
 * subscriber code via {@link HandlerChain#interrupt()} call. If interrupted,
 * further invocation of the handler chain will be barred until the next
 * {@link #post(Object)} call for the event.
 *
 * <p>
 * <b>Subscriber execution</b> mode can be either <em>synchronous</em> or
 * <em>asynchronous</em> depending on the {@link Subscribe} annotation.
 * </p>
 *
 * In case of any error from subscriber code during invocation, the runtime will
 * first search for any {@link ExceptionEvent} handler registered into the
 * system and dispatch the error along with relevant information in
 * {@link ExceptionContext} to the handler if found. If no such error handler is
 * found, runtime will just log the error and move on.
 *
 *
 * @since 1.0
 * @author Anindya Chatterjee.
 * @see Subscribe
 * @see HandlerChain
 * @see HandlerChainAware
 * @see ExceptionEvent
 * @see ExceptionContext
 */
public class JBus<T> {

	private static final Class<?> THIS_CLASS = new Object() {
	}.getClass().getEnclosingClass();
	private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(THIS_CLASS);

	public static void deregister() {
		Entry<JBus<?>, Object> entry = CurrentJBus.INSTANCE.getCurrent();
		Utils.requireNonNull(entry, "could not access current jbus from thread");
		entry.getKey().deregister(entry.getValue());
	}

	private final ListenersRegistry<T> listenersRegistry;
	private final EventDispatcher<T> eventDispatcher;
	private String tag = "";

	public JBus(Class<T> eventType) {
		this(eventType, null);
	}

	/**
	 * Instantiates a new event bus.
	 */
	public JBus(Class<T> busEventType, ExecutorService asyncExecutorService) {
		this.listenersRegistry = new ListenersRegistry<T>(busEventType);
		this.eventDispatcher = new EventDispatcher<T>(this, listenersRegistry,
				asyncExecutorService != null ? asyncExecutorService : Executors.newCachedThreadPool());
	}

	protected <X> void registerObject(Object listener, boolean weak, boolean forceAsync, Class<? extends T> eventType) {
		if (listener == null)
			throw new NullPointerException("Null listener can not be registered.");
		logger.trace("Registering object. listener:{} weak:{}", listener, weak);
		listenersRegistry.register(listener, weak, forceAsync, eventType);
	}

	/**
	 * Registers an event listener to the event bus and keep a strong reference of
	 * the listener object for invocation. To listen to events an object must be
	 * registered first.
	 *
	 * @param listener the listener object.
	 *
	 * @exception JBusException        if the object is already registered or if
	 *                                 there is no subscriber method in its class
	 *                                 hierarchy.
	 * @exception NullPointerException if the object is null.
	 *
	 */
	public void register(Object listener) {
		registerObject(listener, false, false, null);
	}

	public void register(Object listener, Class<? extends T> eventType) {
		registerObject(listener, false, false, eventType);
	}

	public void register(Listener<? extends T> listener) {
		registerObject(listener, false, false, null);
	}

	public <X extends T> void register(Listener<? extends X> listener, Class<X> eventType) {
		registerObject(listener, false, false, eventType);
	}

	public void registerAsync(Listener<? extends T> listener) {
		registerObject(listener, false, true, null);
	}

	public <X extends T> void registerAsync(Listener<? extends X> listener, Class<X> eventType) {
		registerObject(listener, false, true, eventType);
	}

	/**
	 * Registers an event listener to the event bus and keep a weak reference of the
	 * listener object for invocation. To listen to events an object must be
	 * registered first.
	 *
	 * @param listener the listener object.
	 *
	 * @exception JBusException        if the object is already registered or if
	 *                                 there is no subscriber method in its class
	 *                                 hierarchy.
	 * @exception NullPointerException if the object is null.
	 *
	 */
	public void registerWeak(Object listener) {
		registerObject(listener, true, false, null);
	}

	public void registerWeak(Object listener, Class<? extends T> eventType) {
		registerObject(listener, true, false, eventType);
	}

	public void registerWeak(Listener<? extends T> listener) {
		registerObject(listener, true, false, null);
	}

	public <X extends T> void registerWeak(Listener<? extends X> listener, Class<X> eventType) {
		registerObject(listener, true, false, eventType);
	}

	public void registerAsyncWeak(Listener<? extends T> listener) {
		registerObject(listener, true, true, null);
	}

	public <X extends T> void registerAsyncWeak(Listener<? extends X> listener, Class<X> eventType) {
		registerObject(listener, true, true, eventType);
	}

	/**
	 * De-registers a listener object that has been registered with the event bus.
	 * After de-registration, the object cease to listen to any further event.
	 *
	 * @param listener the listener object to deregister.
	 *
	 * @exception NullPointerException if the object is null.
	 *
	 */
	public void deregister(Object listener) {
		if (listener == null)
			throw new NullPointerException("Null object can not be de-registered.");
		logger.trace("Un-Registering listener {}", listener);
		listenersRegistry.deregister(listener);
	}

	public void post(T event) {
		post(event, false);
	}

	/**
	 * Posts an event to the event bus.
	 *
	 * @param event the event to post.
	 *
	 * @exception NullPointerException if the event is null.
	 * @exception JBusException        if the invoking subscriber method throws an
	 *                                 exception.
	 *
	 */
	public void post(T event, boolean requireSubscribers) {
		if (event == null)
			throw new NullPointerException("Null event can not be posted.");
		logger.trace("Event {} has been posted to the bus {}", event, tag);

		List<ListenerMethod> subscribers = listenersRegistry.getSubscribers(event);
		if (subscribers == null || subscribers.isEmpty()) {
			if (requireSubscribers)
				throw new JBusException("Could not find subscribers for event:" + event);
			return;
		}
		logger.trace("Total subscribers found for event {} is = {}", event, subscribers.size());
		logger.trace("Dispatching event {}", event);
		DefaultHandlerChain handlerChain = new DefaultHandlerChain(subscribers);
		eventDispatcher.dispatch(event, handlerChain);
	}

	/**
	 * Sets a tag to the event bus for identification.
	 *
	 * @param tag the tag to set.
	 */
	public void setTag(String tag) {
		this.tag = tag;
	}

	/**
	 * It registers a JVM shutdown hook for graceful shutdown of event bus.
	 *
	 */
	public void addShutdownHook() {
		eventDispatcher.addShutdownHook();
	}
}
