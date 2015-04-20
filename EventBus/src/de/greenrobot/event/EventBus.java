/*
 * Copyright (C) 2012 Markus Junginger, greenrobot (http://greenrobot.de)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.greenrobot.event;

import android.os.Looper;
import android.util.Log;

import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;

/**
 * EventBus is a central publish/subscribe event system for Android. Events are posted ({@link #post(Object)}) to the
 * bus, which delivers it to subscribers that have a matching handler method for the event type. To receive events,
 * subscribers must register themselves to the bus using {@link #register(Object)}. Once registered,
 * subscribers receive events until {@link #unregister(Object)} is called. By convention, event handling methods must
 * be named "onEvent", be public, return nothing (void), and have exactly one parameter (the event).
 *
 * @author Markus Junginger, greenrobot
 */
public class EventBus {

    /** Log tag, apps may override it. */
    public static String TAG = "Event";

    //默认的EventBus实例对象
    static volatile EventBus defaultInstance;

    //默认的EventBusBuilder用于构建默认的EvnetBus
    private static final EventBusBuilder DEFAULT_BUILDER = new EventBusBuilder();
    //zhaosc TODO ???
    private static final Map<Class<?>, List<Class<?>>> eventTypesCache = new HashMap<Class<?>, List<Class<?>>>();

    //根据事件类型，获得订阅者信息列表。key为事件类型，value为订阅者信息类列表（通过事件类型区别订阅者信息）
    private final Map<Class<?>, CopyOnWriteArrayList<Subscription>> subscriptionsByEventType;
    //根据订阅者信息，获得其订阅的事件类型列表（根据订阅者获得其事件类型信息）
    private final Map<Object, List<Class<?>>> typesBySubscriber;
    /**
     * subscriptionsByEventType和typesBySubscriber实现订阅者和事件类型双向定位
     */

    //sticky类型事件，key为事件类型，value为具体事件
    private final Map<Class<?>, Object> stickyEvents;

    //为不同线程隔离PostingThreadState
    private final ThreadLocal<PostingThreadState> currentPostingThreadState = new ThreadLocal<PostingThreadState>() {
        @Override
        protected PostingThreadState initialValue() {
            return new PostingThreadState();
        }
    };


    private final HandlerPoster mainThreadPoster;
    private final BackgroundPoster backgroundPoster;
    private final AsyncPoster asyncPoster;
    //分析订阅者的事件响应方法等信息
    private final SubscriberMethodFinder subscriberMethodFinder;
    private final ExecutorService executorService;

    //当调用事件处理函数异常时是否抛出异常
    private final boolean throwSubscriberException;
    //当调用事件处理函数异常时是否打印异常信息
    private final boolean logSubscriberExceptions;
    //当没有订阅者订阅该事件时是否打印日志
    private final boolean logNoSubscriberMessages;
    //当调用事件处理函数异常时是否发送 SubscriberExceptionEvent 事件，若此开关打开，订阅者可通过
    private final boolean sendSubscriberExceptionEvent;
    private final boolean sendNoSubscriberEvent;
    //是否支持事件继承
    private final boolean eventInheritance;

    /** Convenience singleton for apps using a process-wide EventBus instance. */
    public static EventBus getDefault() {
        if (defaultInstance == null) {
            synchronized (EventBus.class) {
                if (defaultInstance == null) {
                    defaultInstance = new EventBus();
                }
            }
        }
        return defaultInstance;
    }

    public static EventBusBuilder builder() {
        return new EventBusBuilder();
    }

    /** For unit test primarily. */
    public static void clearCaches() {
        SubscriberMethodFinder.clearCaches();
        eventTypesCache.clear();
    }

    /**
     * Creates a new EventBus instance; each instance is a separate scope in which events are delivered. To use a
     * central bus, consider {@link #getDefault()}.
     */
    public EventBus() {
        this(DEFAULT_BUILDER);
    }

    EventBus(EventBusBuilder builder) {
        subscriptionsByEventType = new HashMap<Class<?>, CopyOnWriteArrayList<Subscription>>();
        typesBySubscriber = new HashMap<Object, List<Class<?>>>();
        stickyEvents = new ConcurrentHashMap<Class<?>, Object>();
        mainThreadPoster = new HandlerPoster(this, Looper.getMainLooper(), 10);
        backgroundPoster = new BackgroundPoster(this);
        asyncPoster = new AsyncPoster(this);
        subscriberMethodFinder = new SubscriberMethodFinder(builder.skipMethodVerificationForClasses);
        logSubscriberExceptions = builder.logSubscriberExceptions;
        logNoSubscriberMessages = builder.logNoSubscriberMessages;
        sendSubscriberExceptionEvent = builder.sendSubscriberExceptionEvent;
        sendNoSubscriberEvent = builder.sendNoSubscriberEvent;
        throwSubscriberException = builder.throwSubscriberException;
        eventInheritance = builder.eventInheritance;
        executorService = builder.executorService;
    }


    /**
     * Registers the given subscriber to receive events. Subscribers must call {@link #unregister(Object)} once they
     * are no longer interested in receiving events.
     * <p/>
     * Subscribers have event handling methods that are identified by their name, typically called "onEvent". Event
     * handling methods must have exactly one parameter, the event. If the event handling method is to be called in a
     * specific thread, a modifier is appended to the method name. Valid modifiers match one of the {@link ThreadMode}
     * enums. For example, if a method is to be called in the UI/main thread by EventBus, it would be called
     * "onEventMainThread".
     */
    public void register(Object subscriber) {
        register(subscriber, false, 0);
    }

    /**
     * Like {@link #register(Object)} with an additional subscriber priority to influence the order of event delivery.
     * Within the same delivery thread ({@link ThreadMode}), higher priority subscribers will receive events before
     * others with a lower priority. The default priority is 0. Note: the priority does *NOT* affect the order of
     * delivery among subscribers with different {@link ThreadMode}s!
     */
    public void register(Object subscriber, int priority) {
        register(subscriber, false, priority);
    }

    /**
     * Like {@link #register(Object)}, but also triggers delivery of the most recent sticky event (posted with
     * {@link #postSticky(Object)}) to the given subscriber.
     */
    public void registerSticky(Object subscriber) {
        register(subscriber, true, 0);
    }

    /**
     * Like {@link #register(Object, int)}, but also triggers delivery of the most recent sticky event (posted with
     * {@link #postSticky(Object)}) to the given subscriber.
     */
    public void registerSticky(Object subscriber, int priority) {
        register(subscriber, true, priority);
    }

    private synchronized void register(Object subscriber, boolean sticky, int priority) {
        //分析订阅者订响应方法信息，一个订阅者可能订阅了多个响应方法
        List<SubscriberMethod> subscriberMethods = subscriberMethodFinder.findSubscriberMethods(subscriber.getClass());
        for (SubscriberMethod subscriberMethod : subscriberMethods) {
            subscribe(subscriber, subscriberMethod, sticky, priority);
        }
    }

    // Must be called in synchronized block
    // why? Because using CopyOnWriteArrayList
    private void subscribe(Object subscriber, SubscriberMethod subscriberMethod, boolean sticky, int priority) {
        //获得订阅者的事件类型
        Class<?> eventType = subscriberMethod.eventType;
        //获得已订阅eventType的订阅者信息列表
        CopyOnWriteArrayList<Subscription> subscriptions = subscriptionsByEventType.get(eventType);
        //构造订阅者
        Subscription newSubscription = new Subscription(subscriber, subscriberMethod, priority);
        if (subscriptions == null) {
            //如果目前没有订阅eventType事件类型的订阅者
            //构建订阅者列表
            subscriptions = new CopyOnWriteArrayList<Subscription>();
            //加入缓存中
            subscriptionsByEventType.put(eventType, subscriptions);
        } else {
            //重复订阅，抛出异常
            if (subscriptions.contains(newSubscription)) {
                throw new EventBusException("Subscriber " + subscriber.getClass() + " already registered to event "
                        + eventType);
            }
        }

        // Starting with EventBus 2.2 we enforced methods to be public (might change with annotations again)
        // subscriberMethod.method.setAccessible(true);

        int size = subscriptions.size();
        //根据订阅者的优先级，插入订阅者列表中
        for (int i = 0; i <= size; i++) {
            if (i == size || newSubscription.priority > subscriptions.get(i).priority) {
                subscriptions.add(i, newSubscription);
                break;
            }
        }


        //获得订阅者已订阅事件类型列表
        List<Class<?>> subscribedEvents = typesBySubscriber.get(subscriber);
        if (subscribedEvents == null) {
            subscribedEvents = new ArrayList<Class<?>>();
            typesBySubscriber.put(subscriber, subscribedEvents);
        }
        //更新订阅者对应的事件类型列表
        subscribedEvents.add(eventType);


        if (sticky) {
            //如果为sticky事件
            if (eventInheritance) {
                // Existing sticky events of all subclasses of eventType have to be considered.
                // Note: Iterating over all events may be inefficient with lots of sticky events,
                // thus data structure should be changed to allow a more efficient lookup
                // (e.g. an additional map storing sub classes of super classes: Class -> List<Class>).
                Set<Map.Entry<Class<?>, Object>> entries = stickyEvents.entrySet();
                for (Map.Entry<Class<?>, Object> entry : entries) {
                    Class<?> candidateEventType = entry.getKey();
                    if (eventType.isAssignableFrom(candidateEventType)) {
                        Object stickyEvent = entry.getValue();
                        checkPostStickyEventToSubscription(newSubscription, stickyEvent);
                    }
                }
            } else {
                Object stickyEvent = stickyEvents.get(eventType);
                checkPostStickyEventToSubscription(newSubscription, stickyEvent);
            }
        }
    }

    private void checkPostStickyEventToSubscription(Subscription newSubscription, Object stickyEvent) {
        if (stickyEvent != null) {
            // If the subscriber is trying to abort the event, it will fail (event is not tracked in posting state)
            // --> Strange corner case, which we don't take care of here.
            postToSubscription(newSubscription, stickyEvent, Looper.getMainLooper() == Looper.myLooper());
        }
    }

    /**
     * 判断subscriber是否已订阅
     * @param subscriber
     * @return
     */
    public synchronized boolean isRegistered(Object subscriber) {
        return typesBySubscriber.containsKey(subscriber);
    }

    /**
     * Only updates subscriptionsByEventType, not typesBySubscriber! Caller must update typesBySubscriber.
     * 根据订阅者和事件类型取消订阅
     * @param subscriber
     * @param eventType
     */
    private void unubscribeByEventType(Object subscriber, Class<?> eventType) {
        //获得已订阅事件类型eventType的所有订阅者信息
        List<Subscription> subscriptions = subscriptionsByEventType.get(eventType);
        if (subscriptions != null) {
            int size = subscriptions.size();
            for (int i = 0; i < size; i++) {
                Subscription subscription = subscriptions.get(i);
                //如果订阅者信息与需要取消的subscriber匹配，则移除
                if (subscription.subscriber == subscriber) {
                    subscription.active = false;
                    subscriptions.remove(i);
                    i--;
                    size--;
                }
            }
        }
    }

    /**
     * Unregisters the given subscriber from all event classes.
     * @param subscriber
     */
    public synchronized void unregister(Object subscriber) {
        //获得订阅者已订阅的事件类型
        List<Class<?>> subscribedTypes = typesBySubscriber.get(subscriber);
        if (subscribedTypes != null) {
            for (Class<?> eventType : subscribedTypes) {
                //根据订阅者，以及其订阅的事件类型（两个维度定位），取消订阅
                unubscribeByEventType(subscriber, eventType);
            }
            typesBySubscriber.remove(subscriber);
        } else {
            Log.w(TAG, "Subscriber to unregister was not registered before: " + subscriber.getClass());
        }
    }

    /**
     * Posts the given event to the event bus.
     * @param event
     */
    public void post(Object event) {
        //线程Post信息
        PostingThreadState postingState = currentPostingThreadState.get();
        //加入事件队列
        List<Object> eventQueue = postingState.eventQueue;
        eventQueue.add(event);

        if (!postingState.isPosting) {
            postingState.isMainThread = Looper.getMainLooper() == Looper.myLooper();
            postingState.isPosting = true;
            if (postingState.canceled) {
                throw new EventBusException("Internal error. Abort state was not reset");
            }
            try {
                while (!eventQueue.isEmpty()) {
                    postSingleEvent(eventQueue.remove(0), postingState);
                }
            } finally {
                postingState.isPosting = false;
                postingState.isMainThread = false;
            }
        }
    }

    /**
     * Called from a subscriber's event handling method, further event delivery will be canceled. Subsequent
     * subscribers
     * won't receive the event. Events are usually canceled by higher priority subscribers (see
     * {@link #register(Object, int)}). Canceling is restricted to event handling methods running in posting thread
     * {@link ThreadMode#PostThread}.
     */
    public void cancelEventDelivery(Object event) {
        PostingThreadState postingState = currentPostingThreadState.get();
        if (!postingState.isPosting) {
            throw new EventBusException(
                    "This method may only be called from inside event handling methods on the posting thread");
        } else if (event == null) {
            throw new EventBusException("Event may not be null");
        } else if (postingState.event != event) {
            throw new EventBusException("Only the currently handled event may be aborted");
        } else if (postingState.subscription.subscriberMethod.threadMode != ThreadMode.PostThread) {
            throw new EventBusException(" event handlers may only abort the incoming event");
        }

        postingState.canceled = true;
    }

    /**
     * Posts the given event to the event bus and holds on to the event (because it is sticky). The most recent sticky
     * event of an event's type is kept in memory for future access. This can be {@link #registerSticky(Object)} or
     * {@link #getStickyEvent(Class)}.
     */
    public void postSticky(Object event) {
        synchronized (stickyEvents) {
            stickyEvents.put(event.getClass(), event);
        }
        // Should be posted after it is putted, in case the subscriber wants to remove immediately
        post(event);
    }

    /**
     * Gets the most recent sticky event for the given type.
     *
     * @see #postSticky(Object)
     */
    public <T> T getStickyEvent(Class<T> eventType) {
        synchronized (stickyEvents) {
            return eventType.cast(stickyEvents.get(eventType));
        }
    }

    /**
     * Remove and gets the recent sticky event for the given event type.
     *
     * @see #postSticky(Object)
     */
    public <T> T removeStickyEvent(Class<T> eventType) {
        synchronized (stickyEvents) {
            return eventType.cast(stickyEvents.remove(eventType));
        }
    }

    /**
     * Removes the sticky event if it equals to the given event.
     *
     * @return true if the events matched and the sticky event was removed.
     */
    public boolean removeStickyEvent(Object event) {
        synchronized (stickyEvents) {
            Class<?> eventType = event.getClass();
            Object existingEvent = stickyEvents.get(eventType);
            if (event.equals(existingEvent)) {
                stickyEvents.remove(eventType);
                return true;
            } else {
                return false;
            }
        }
    }

    /**
     * Removes all sticky events.
     */
    public void removeAllStickyEvents() {
        synchronized (stickyEvents) {
            stickyEvents.clear();
        }
    }

    public boolean hasSubscriberForEvent(Class<?> eventClass) {
        List<Class<?>> eventTypes = lookupAllEventTypes(eventClass);
        if (eventTypes != null) {
            int countTypes = eventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = eventTypes.get(h);
                CopyOnWriteArrayList<Subscription> subscriptions;
                synchronized (this) {
                    subscriptions = subscriptionsByEventType.get(clazz);
                }
                if (subscriptions != null && !subscriptions.isEmpty()) {
                    return true;
                }
            }
        }
        return false;
    }

    private void postSingleEvent(Object event, PostingThreadState postingState) throws Error {
        Class<?> eventClass = event.getClass();
        boolean subscriptionFound = false;
        if (eventInheritance) {
            List<Class<?>> eventTypes = lookupAllEventTypes(eventClass);
            int countTypes = eventTypes.size();
            for (int h = 0; h < countTypes; h++) {
                Class<?> clazz = eventTypes.get(h);
                subscriptionFound |= postSingleEventForEventType(event, postingState, clazz);
            }
        } else {
            subscriptionFound = postSingleEventForEventType(event, postingState, eventClass);
        }
        if (!subscriptionFound) {
            if (logNoSubscriberMessages) {
                Log.d(TAG, "No subscribers registered for event " + eventClass);
            }
            if (sendNoSubscriberEvent && eventClass != NoSubscriberEvent.class &&
                    eventClass != SubscriberExceptionEvent.class) {
                post(new NoSubscriberEvent(this, event));
            }
        }
    }

    private boolean postSingleEventForEventType(Object event, PostingThreadState postingState, Class<?> eventClass) {
        CopyOnWriteArrayList<Subscription> subscriptions;
        synchronized (this) {
            subscriptions = subscriptionsByEventType.get(eventClass);
        }
        if (subscriptions != null && !subscriptions.isEmpty()) {
            for (Subscription subscription : subscriptions) {
                postingState.event = event;
                postingState.subscription = subscription;
                boolean aborted = false;
                try {
                    postToSubscription(subscription, event, postingState.isMainThread);
                    aborted = postingState.canceled;
                } finally {
                    postingState.event = null;
                    postingState.subscription = null;
                    postingState.canceled = false;
                }
                if (aborted) {
                    break;
                }
            }
            return true;
        }
        return false;
    }

    private void postToSubscription(Subscription subscription, Object event, boolean isMainThread) {
        switch (subscription.subscriberMethod.threadMode) {
            case PostThread:
                invokeSubscriber(subscription, event);
                break;
            case MainThread:
                if (isMainThread) {
                    invokeSubscriber(subscription, event);
                } else {
                    mainThreadPoster.enqueue(subscription, event);
                }
                break;
            case BackgroundThread:
                if (isMainThread) {
                    backgroundPoster.enqueue(subscription, event);
                } else {
                    invokeSubscriber(subscription, event);
                }
                break;
            case Async:
                asyncPoster.enqueue(subscription, event);
                break;
            default:
                throw new IllegalStateException("Unknown thread mode: " + subscription.subscriberMethod.threadMode);
        }
    }

    /** Looks up all Class objects including super classes and interfaces. Should also work for interfaces. */
    private List<Class<?>> lookupAllEventTypes(Class<?> eventClass) {
        synchronized (eventTypesCache) {
            List<Class<?>> eventTypes = eventTypesCache.get(eventClass);
            if (eventTypes == null) {
                eventTypes = new ArrayList<Class<?>>();
                Class<?> clazz = eventClass;
                while (clazz != null) {
                    eventTypes.add(clazz);
                    addInterfaces(eventTypes, clazz.getInterfaces());
                    clazz = clazz.getSuperclass();
                }
                eventTypesCache.put(eventClass, eventTypes);
            }
            return eventTypes;
        }
    }

    /** Recurses through super interfaces. */
    static void addInterfaces(List<Class<?>> eventTypes, Class<?>[] interfaces) {
        for (Class<?> interfaceClass : interfaces) {
            if (!eventTypes.contains(interfaceClass)) {
                eventTypes.add(interfaceClass);
                addInterfaces(eventTypes, interfaceClass.getInterfaces());
            }
        }
    }

    /**
     * Invokes the subscriber if the subscriptions is still active. Skipping subscriptions prevents race conditions
     * between {@link #unregister(Object)} and event delivery. Otherwise the event might be delivered after the
     * subscriber unregistered. This is particularly important for main thread delivery and registrations bound to the
     * live cycle of an Activity or Fragment.
     */
    void invokeSubscriber(PendingPost pendingPost) {
        Object event = pendingPost.event;
        Subscription subscription = pendingPost.subscription;
        PendingPost.releasePendingPost(pendingPost);
        if (subscription.active) {
            invokeSubscriber(subscription, event);
        }
    }

    void invokeSubscriber(Subscription subscription, Object event) {
        try {
            subscription.subscriberMethod.method.invoke(subscription.subscriber, event);
        } catch (InvocationTargetException e) {
            handleSubscriberException(subscription, event, e.getCause());
        } catch (IllegalAccessException e) {
            throw new IllegalStateException("Unexpected exception", e);
        }
    }

    private void handleSubscriberException(Subscription subscription, Object event, Throwable cause) {
        if (event instanceof SubscriberExceptionEvent) {
            if (logSubscriberExceptions) {
                // Don't send another SubscriberExceptionEvent to avoid infinite event recursion, just log
                Log.e(TAG, "SubscriberExceptionEvent subscriber " + subscription.subscriber.getClass()
                        + " threw an exception", cause);
                SubscriberExceptionEvent exEvent = (SubscriberExceptionEvent) event;
                Log.e(TAG, "Initial event " + exEvent.causingEvent + " caused exception in "
                        + exEvent.causingSubscriber, exEvent.throwable);
            }
        } else {
            if (throwSubscriberException) {
                throw new EventBusException("Invoking subscriber failed", cause);
            }
            if (logSubscriberExceptions) {
                Log.e(TAG, "Could not dispatch event: " + event.getClass() + " to subscribing class "
                        + subscription.subscriber.getClass(), cause);
            }
            if (sendSubscriberExceptionEvent) {
                SubscriberExceptionEvent exEvent = new SubscriberExceptionEvent(this, cause, event,
                        subscription.subscriber);
                post(exEvent);
            }
        }
    }

    /** For ThreadLocal, much faster to set (and get multiple values). */
    final static class PostingThreadState {
        //事件队列
        final List<Object> eventQueue = new ArrayList<Object>();
        //是否分发中
        boolean isPosting;
        //是否为主线程
        boolean isMainThread;
        //订阅者信息
        Subscription subscription;
        //事件
        Object event;
        //是否取消
        boolean canceled;
    }

    ExecutorService getExecutorService() {
        return executorService;
    }

    // Just an idea: we could provide a callback to post() to be notified, an alternative would be events, of course...
    /* public */interface PostCallback {
        void onPostCompleted(List<SubscriberExceptionEvent> exceptionEvents);
    }

}
