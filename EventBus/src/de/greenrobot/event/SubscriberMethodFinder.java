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

import android.util.Log;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 订阅者事件响应方法分析器
 */
class SubscriberMethodFinder {
    private static final String ON_EVENT_METHOD_NAME = "onEvent";

    /*
     * In newer class files, compilers may add methods. Those are called bridge or synthetic methods.
     * EventBus must ignore both. There modifiers are not public but defined in the Java class file format:
     * http://docs.oracle.com/javase/specs/jvms/se7/html/jvms-4.html#jvms-4.6-200-A.1
     */
    private static final int BRIDGE = 0x40;
    private static final int SYNTHETIC = 0x1000;

    private static final int MODIFIERS_IGNORE = Modifier.ABSTRACT | Modifier.STATIC | BRIDGE | SYNTHETIC;
    //缓存订阅者实现的事件响应方法。key为订阅者类名称，value为订阅者实现的事件响应方法列表
    private static final Map<String, List<SubscriberMethod>> methodCache = new HashMap<String, List<SubscriberMethod>>();

    //跳过事件响应方法严格校验的类
    private final Map<Class<?>, Class<?>> skipMethodVerificationForClasses;

    //构造订阅者事件响应方法分析器
    SubscriberMethodFinder(List<Class<?>> skipMethodVerificationForClassesList) {
        skipMethodVerificationForClasses = new ConcurrentHashMap<Class<?>, Class<?>>();
        if (skipMethodVerificationForClassesList != null) {
            for (Class<?> clazz : skipMethodVerificationForClassesList) {
                skipMethodVerificationForClasses.put(clazz, clazz);
            }
        }
    }

    /**
     * 分析订阅者subscriberClass其中的事件响应方法
     * @param subscriberClass
     * @return
     */
    List<SubscriberMethod> findSubscriberMethods(Class<?> subscriberClass) {
        //key为订阅者类名称
        String key = subscriberClass.getName();
        List<SubscriberMethod> subscriberMethods;
        //查看缓存，如果已分析过，直接返回事件响应方法列表
        synchronized (methodCache) {
            subscriberMethods = methodCache.get(key);
        }
        if (subscriberMethods != null) {
            return subscriberMethods;
        }

        subscriberMethods = new ArrayList<SubscriberMethod>();
        Class<?> clazz = subscriberClass;
        //保存在分析subscriberClass中，已经分析过的事件响应方法的标示
        HashSet<String> eventTypesFound = new HashSet<String>();
        StringBuilder methodKeyBuilder = new StringBuilder();
        //这里使用while循环，主要是为了分析clazz的父类中的事件响应方法列表，clazz同时支持父类的事件响应列表
        while (clazz != null) {
            String name = clazz.getName();
            //过滤掉java和android自带类分析
            if (name.startsWith("java.") || name.startsWith("javax.") || name.startsWith("android.")) {
                // Skip system classes, this just degrades performance
                break;
            }

            // Starting with EventBus 2.2 we enforced methods to be public (might change with annotations again)
            //获得clazz的方法类表
            Method[] methods = clazz.getDeclaredMethods();
            for (Method method : methods) {
                String methodName = method.getName();
                //逐个分析，是否符合EventBus事件响应方法规范
                if (methodName.startsWith(ON_EVENT_METHOD_NAME)) {
                    int modifiers = method.getModifiers();
                    if ((modifiers & Modifier.PUBLIC) != 0 && (modifiers & MODIFIERS_IGNORE) == 0) {
                        Class<?>[] parameterTypes = method.getParameterTypes();
                        //从这里可以得出结论，EventBus只支持单参数定义事件响应方法
                        if (parameterTypes.length == 1) {
                            String modifierString = methodName.substring(ON_EVENT_METHOD_NAME.length());
                            ThreadMode threadMode;
                            //根据方法名称，确定ThreadMode
                            if (modifierString.length() == 0) {
                                threadMode = ThreadMode.PostThread;
                            } else if (modifierString.equals("MainThread")) {
                                threadMode = ThreadMode.MainThread;
                            } else if (modifierString.equals("BackgroundThread")) {
                                threadMode = ThreadMode.BackgroundThread;
                            } else if (modifierString.equals("Async")) {
                                threadMode = ThreadMode.Async;
                            } else {
                                if (skipMethodVerificationForClasses.containsKey(clazz)) {
                                    //真的clazz不严格校验事件响应方法
                                    continue;
                                } else {
                                    throw new EventBusException("Illegal onEvent method, check for typos: " + method);
                                }
                            }
                            //由事件响应方法第一个参数定义事件类型
                            Class<?> eventType = parameterTypes[0];
                            methodKeyBuilder.setLength(0);
                            methodKeyBuilder.append(methodName);
                            methodKeyBuilder.append('>').append(eventType.getName());
                            String methodKey = methodKeyBuilder.toString();
                            if (eventTypesFound.add(methodKey)) {
                                // Only add if not already found in a sub class
                                //添加事件响应方法信息
                                subscriberMethods.add(new SubscriberMethod(method, threadMode, eventType));
                            }
                        }
                    } else if (!skipMethodVerificationForClasses.containsKey(clazz)) {
                        Log.d(EventBus.TAG, "Skipping method (not public, static or abstract): " + clazz + "."
                                + methodName);
                    }
                }
            }
            clazz = clazz.getSuperclass();
        }

        //未实现事件响应方法，抛出异常
        if (subscriberMethods.isEmpty()) {
            throw new EventBusException("Subscriber " + subscriberClass + " has no public methods called "
                    + ON_EVENT_METHOD_NAME);
        } else {
            //分析成功，分析结果加入缓存
            synchronized (methodCache) {
                methodCache.put(key, subscriberMethods);
            }
            return subscriberMethods;
        }
    }

    //清楚分析缓存
    static void clearCaches() {
        synchronized (methodCache) {
            methodCache.clear();
        }
    }

}
