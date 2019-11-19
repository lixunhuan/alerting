package com.amazon.opendistroforelasticsearch.alerting.core.auth

import org.elasticsearch.common.util.concurrent.ThreadContext

import java.lang.reflect.Field
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method

object ExtendThreadContextManager {
    private var contextClass: Class<*>? = null
    fun load() {

        for (c in ThreadContext::class.java.declaredClasses) {
            if (c.simpleName === "ThreadContextStruct") {
                contextClass = c

            }
        }
        //        Field field = ThreadContext.class.getDeclaredField(filedName);
        //        field.setAccessible(true);
        //        return field.get(threadContext);
    }

    private object FakeThreadContext {
        internal var threadLocalField: Field? = null
        private val field: Field?
            @Synchronized get() {
                if (threadLocalField == null) {
                    try {
                        threadLocalField = ThreadContext::class.java.getDeclaredField("threadLocal")
                        threadLocalField!!.isAccessible = true
                    } catch (e: NoSuchFieldException) {
                        e.printStackTrace()
                    }

                }
                return threadLocalField
            }

        @Throws(IllegalAccessException::class)
        fun reflectGetThreadLocal(threadContext: Any): Any {
            return field!!.get(threadContext)
        }
    }

    private object FakeContextThreadLocal {
        internal var threadLocalGetMethod: Method? = null
        @Throws(NoSuchMethodException::class)
        private fun getMethod(contextThreadLocalObject: Any): Method {
            if (threadLocalGetMethod == null) {
                threadLocalGetMethod = contextThreadLocalObject.javaClass.getDeclaredMethod("get")
                threadLocalGetMethod!!.isAccessible = true
            }
            return threadLocalGetMethod as Method
        }

        @Throws(InvocationTargetException::class, IllegalAccessException::class, NoSuchMethodException::class)
        fun reflectThreadLocalGetMethod(contextThreadLocalObject: Any): Any {
            return getMethod(contextThreadLocalObject).invoke(contextThreadLocalObject)
        }
    }

    private object FakeThreadContextStruct {
        internal var transientHeadersField: Field? = null
        internal var requestHeadersField: Field? = null
        @Synchronized
        private fun getTransientHeadersField(threadContextStructObject: Any): Field? {
            if (transientHeadersField == null) {
                try {
                    transientHeadersField = threadContextStructObject.javaClass.getDeclaredField("transientHeaders")
                    transientHeadersField!!.isAccessible = true
                } catch (e: NoSuchFieldException) {
                    e.printStackTrace()
                }

            }
            return transientHeadersField
        }

        @Synchronized
        fun getRequestHeadersField(threadContextStructObject: Any): Field? {
            if (requestHeadersField == null) {
                try {
                    requestHeadersField = threadContextStructObject.javaClass.getDeclaredField("requestHeaders")
                    requestHeadersField!!.isAccessible = true
                } catch (e: NoSuchFieldException) {
                    e.printStackTrace()
                }

            }
            return requestHeadersField
        }

        @Throws(IllegalAccessException::class)
        fun getTransientHeaders(threadContextStructObject: Any): Any {
            return getTransientHeadersField(threadContextStructObject)!!.get(threadContextStructObject)
        }

        @Throws(IllegalAccessException::class)
        fun getRequestHeaders(threadContextStructObject: Any): Any {
            return getRequestHeadersField(threadContextStructObject)!!.get(threadContextStructObject)
        }

        @Throws(IllegalAccessException::class)
        fun setTransientHeaders(threadContextStructObject: Any, headers: Any) {
            getTransientHeadersField(threadContextStructObject)!!.set(threadContextStructObject, headers)
        }

        @Throws(IllegalAccessException::class)
        fun setRequestHeaders(threadContextStructObject: Any, headers: Any) {
            getRequestHeadersField(threadContextStructObject)!!.set(threadContextStructObject, headers)
        }
    }

    class ContextRecover constructor(internal var threadLocalContext: Any, internal var transientHeaders: Any, internal var requestHeaders: Any) {
        @Throws(IllegalAccessException::class)
        fun recover() {
            ExtendThreadContextManager.FakeThreadContextStruct.setTransientHeaders(threadLocalContext, transientHeaders)
            ExtendThreadContextManager.FakeThreadContextStruct.setRequestHeaders(threadLocalContext, requestHeaders)
        }
    }

    fun clean(threadContext: ThreadContext, key: String): ContextRecover {
        val contextThreadLocalObject = ExtendThreadContextManager.FakeThreadContext.reflectGetThreadLocal(threadContext)
        val threadLocalContext = ExtendThreadContextManager.FakeContextThreadLocal.reflectThreadLocalGetMethod(contextThreadLocalObject)
        val transientHeaders = ExtendThreadContextManager.FakeThreadContextStruct.getTransientHeaders(threadLocalContext)
        @Suppress("UNCHECKED_CAST")
        (ExtendThreadContextManager.FakeThreadContextStruct.setTransientHeaders(threadLocalContext, (transientHeaders as Map<String, Any>).minus(key)))

        val requestHeaders = ExtendThreadContextManager.FakeThreadContextStruct.getRequestHeaders(threadLocalContext)
        @Suppress("UNCHECKED_CAST")
        (ExtendThreadContextManager.FakeThreadContextStruct.setRequestHeaders(threadLocalContext, (requestHeaders as Map<String, Any>).minus(key)))
        return ContextRecover(threadLocalContext, transientHeaders, requestHeaders)
    }

}
