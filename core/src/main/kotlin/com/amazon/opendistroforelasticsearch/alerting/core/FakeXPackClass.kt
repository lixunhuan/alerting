package com.amazon.opendistroforelasticsearch.alerting.core

import org.elasticsearch.Version

import java.lang.reflect.Constructor
import java.lang.reflect.InvocationTargetException

class FakeXPackClass {

    object FakeUser {
        var clazz: Class<*>? = null
        var userConstructor: Constructor<*>? = null
        @Throws(NoSuchMethodException::class)
        fun setup(targetClass: Class<*>) {
            clazz = targetClass
            //todo
            // userConstructor = targetClass.getConstructor(Boolean::class.javaPrimitiveType)
        }

        fun loadClass(): Class<*> {
            return clazz!!
        }

        @Throws(IllegalAccessException::class, InvocationTargetException::class, InstantiationException::class)
        fun newInstance(b: Boolean): Any {
            return userConstructor!!.newInstance(b)
        }
    }

    object FakeElasticUser {
        var clazz: Class<*>? = null
        var userConstructor: Constructor<*>? = null
        @Throws(NoSuchMethodException::class)
        fun setup(targetClass: Class<*>) {
            clazz = targetClass
            userConstructor = targetClass.getConstructor(Boolean::class.javaPrimitiveType)
        }

        fun loadClass(): Class<*> {
            return clazz!!
        }

        @Throws(IllegalAccessException::class, InvocationTargetException::class, InstantiationException::class)
        fun newInstance(b: Boolean): Any {
            return userConstructor!!.newInstance(b)
        }
    }

    object FakeAuthenticationRealm {
        var clazz: Class<*>? = null
        var userConstructor: Constructor<*>? = null
        fun loadClass(): Class<*> {
            return clazz!!
        }

        @Throws(NoSuchMethodException::class)
        fun setup(targetClass: Class<*>) {
            clazz = targetClass
            userConstructor = targetClass.getConstructor(String::class.java, String::class.java, String::class.java)
        }

        @Throws(IllegalAccessException::class, InvocationTargetException::class, InstantiationException::class)
        fun newInstance(name: String, type: String, nodeName: String): Any {
            return userConstructor!!.newInstance(name, type, nodeName)
        }
    }

    class FakeAuthenticationType{
        companion object {
            var clazz: Class<*>? = null
            fun loadClass(): Class<*> {
                return clazz!!
            }

            var realm: Any? = null
                internal set
            var apI_KEY: Any? = null
                internal set
            var token: Any? = null
                internal set
            var anonymous: Any? = null
                internal set
            var internal: Any? = null
                internal set

            @Throws(NoSuchMethodException::class)
            fun setup(targetClass: Class<*>) {
                clazz = targetClass
                for (enumConst in targetClass.enumConstants) {
                    when (enumConst.toString()) {
                        "REALM" -> {
                            realm = enumConst
                            apI_KEY = enumConst
                            token = enumConst
                            anonymous = enumConst
                            internal = enumConst
                        }
                        "API_KEY" -> {
                            apI_KEY = enumConst
                            token = enumConst
                            anonymous = enumConst
                            internal = enumConst
                        }
                        "TOKEN" -> {
                            token = enumConst
                            anonymous = enumConst
                            internal = enumConst
                        }
                        "ANONYMOUS" -> {
                            anonymous = enumConst
                            internal = enumConst
                        }
                        "INTERNAL" -> internal = enumConst
                    }
                }
            }
        }

    }

    object FakeAuthentication {
        var clazz: Class<*>? = null
        var userConstructor: Constructor<*>? = null
        fun loadClass(): Class<*> {
            return clazz!!
        }

        @Throws(NoSuchMethodException::class)
        fun setup(targetClass: Class<*>) {
            clazz = targetClass
            //(userClass, FakeXPackClass.FakeAuthenticationRealm.clazz, FakeXPackClass.FakeAuthenticationRealm.clazz,
            // Version::class.java, typeClass, Map::class.java)

        }

        @Throws(NoSuchMethodException::class)
        fun lazy(targetClass: Class<*>) {
            //(userClass, FakeXPackClass.FakeAuthenticationRealm.clazz, FakeXPackClass.FakeAuthenticationRealm.clazz,
            // Version::class.java, typeClass, Map::class.java)
            userConstructor = targetClass.getConstructor(
                    FakeUser.loadClass(),
                    FakeAuthenticationRealm.loadClass(),
                    FakeAuthenticationRealm.loadClass(),
                    Version::class.java,
                    FakeAuthenticationType.loadClass(),
                    Map::class.java)
        }
        @Throws(IllegalAccessException::class, InvocationTargetException::class, InstantiationException::class)
        fun newInstance(user: Any, authenticatedBy: Any?, lookedUpBy: Any?, version: Any,
                        type: Any, metadata: Any): Any {
            return userConstructor!!.newInstance(user, authenticatedBy, lookedUpBy, version, type, metadata)
        }
    }
}
