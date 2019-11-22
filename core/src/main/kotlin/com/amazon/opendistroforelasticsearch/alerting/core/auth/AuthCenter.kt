package com.amazon.opendistroforelasticsearch.alerting.core.auth

import org.elasticsearch.Version
import org.elasticsearch.common.util.concurrent.ThreadContext
import java.lang.reflect.InvocationTargetException
import java.util.HashMap

class AuthCenter(private val tContext: ThreadContext) {

    companion object {
        private var target: AuthCenter? = null
        private const val XPACK_SECURITY_AUTH_HEADER = "_xpack_security_authentication"
        fun get(): AuthCenter? {
            return target
        }

        @Synchronized
        fun setUpAuthContextOnAlertPluginInit(tContext: ThreadContext) {
//            println("${Thread.currentThread().name} : try set up")
//            for(line in Thread.currentThread().stackTrace){
//                println("${line.className}.${line.methodName}.${line.lineNumber}")
//            }
            val ac = AuthCenter(tContext)
            ac.loadClass()
            target = ac
        }

        @Synchronized
        fun <T> runWithElasticUser(callback: () -> T): T {
            return callback()
        }

        @Synchronized
        fun getCurrentUserName(): String? {
            val authentication: Any? = target!!.tContext.getTransient<Any>(XPACK_SECURITY_AUTH_HEADER) ?: return null
            val user = authentication!!.javaClass.getDeclaredMethod("getUser").invoke(authentication)
            return FakeXPackClass.FakeUser.loadClass().getDeclaredMethod("principal").invoke(user) as String
        }

        @Synchronized
        fun getCurrentUserRole(): Array<String>? {
            val authentication: Any? = target!!.tContext.getTransient<Any>(XPACK_SECURITY_AUTH_HEADER) ?: return null
            val user = authentication!!.javaClass.getDeclaredMethod("getUser").invoke(authentication)
            return FakeXPackClass.FakeUser.loadClass().getDeclaredMethod("roles").invoke(user) as Array<String>
        }

        @Synchronized
        fun <T> execWithElasticUser(callback: () -> T): T {
            val needToRecoverCurrentAuthentication =
                    target!!.tContext.getTransient<Any>(XPACK_SECURITY_AUTH_HEADER) != null

            if (needToRecoverCurrentAuthentication) {
                val recover = ExtendThreadContextManager.clean(target!!.tContext, XPACK_SECURITY_AUTH_HEADER);
                //println("replace with mariya authentication")
                target?.setElasticUserToContext();
                val result = callback();
                //println("recover original authentication")
                recover.recover();
                return result
            } else {
                //println("insert with mariya authentication")
                target?.setElasticUserToContext();
                val result = callback();
                ExtendThreadContextManager.clean(target!!.tContext, XPACK_SECURITY_AUTH_HEADER);
                //println("clean authentication")
                return result
            }
        }
    }

    private val klazzes = HashMap<String, Any>()
    @Synchronized
    private fun loadClass() {
        val globalLoaderClass = AuthCenter::class.java.classLoader.parent.javaClass
        val method = globalLoaderClass.getMethod("globalFindClass", String::class.java)
        FakeXPackClass.FakeAuthentication.setup(method.invoke(null, "org.elasticsearch.xpack.core.security.authc.Authentication") as Class<*>)
        for (clazz in FakeXPackClass.FakeAuthentication.loadClass().classes) {
            if (clazz.simpleName == "AuthenticationType") {
                FakeXPackClass.FakeAuthenticationType.setup(clazz)
            } else if (clazz.simpleName == "RealmRef") {
                FakeXPackClass.FakeAuthenticationRealm.setup(clazz)
            }
        }
        FakeXPackClass.FakeElasticUser.setup(method.invoke(null, "org.elasticsearch.xpack.core.security.user.ElasticUser") as Class<*>)
        FakeXPackClass.FakeUser.setup(method.invoke(null, "org.elasticsearch.xpack.core.security.user.User") as Class<*>)
        FakeXPackClass.FakeAuthentication.lazy(method.invoke(null, "org.elasticsearch.xpack.core.security.authc.Authentication") as Class<*>)

    }

    private var authentication: Any? = null
    @Synchronized
    fun buildElasticUserAuthentication(): Any? {
        if (authentication == null) {
            val userClass = FakeXPackClass.FakeUser.loadClass()
            //val user = FakeXPackClass.FakeElasticUser.newInstance(true)
//            val roles = arrayOf("tester")
//            val user = userClass.getConstructor(String::class.java, Array<String>::class.java, String::class.java, String::class.java, Map::class.java, Boolean::class.javaPrimitiveType)
//                    .newInstance("maliya", roles, "maliya", "", null, true)
            val roles = arrayOf("superuser")
            val user = userClass.getConstructor(String::class.java, Array<String>::class.java, String::class.java, String::class.java, Map::class.java, Boolean::class.javaPrimitiveType)
                    .newInstance("elastic", roles, "kibana", "", null, true)
            val realm = FakeXPackClass.FakeAuthenticationRealm.newInstance("__attach", "__attach", "nodeName")
            // val typeClass = FakeXPackClass.FakeAuthenticationType.loadClass()
            val internalType = FakeXPackClass.FakeAuthenticationType.internal
            authentication = FakeXPackClass.FakeAuthentication.newInstance(user, realm, null, Version.CURRENT, internalType!!, emptyMap<Any, Any>())

        }
        return authentication
    }

    fun setElasticUserToContext() {
        if (tContext.getTransient<Any>(XPACK_SECURITY_AUTH_HEADER) == null) {
            //println("${Thread.currentThread().name} : generate elastic user")
            buildElasticUserAuthentication()!!.javaClass.getMethod("writeToContext", ThreadContext::class.java)
                    .invoke(authentication, tContext)

        } else {
            throw  RuntimeException("$XPACK_SECURITY_AUTH_HEADER header already exist")
        }

    }

    fun getCurrentUser(): Any? {
        return tContext.getTransient<Any>(XPACK_SECURITY_AUTH_HEADER)
    }

    fun setCurrentUser(user: Any?) {
        tContext.putTransient(XPACK_SECURITY_AUTH_HEADER, user)
    }

    private fun copyUser(threadContext: ThreadContext): Boolean? {
        val customerAuthentication = threadContext.getTransient<Any>(XPACK_SECURITY_AUTH_HEADER)
                ?: throw RuntimeException("no $XPACK_SECURITY_AUTH_HEADER transient in context.")
        val authenticationClass = FakeXPackClass.FakeAuthentication.loadClass()
        try {
            val getUserMethod = authenticationClass.getMethod("getUser")
            val userObject = getUserMethod.invoke(customerAuthentication)
            val userClass = FakeXPackClass.FakeUser.loadClass()
            val getUsernameMethod = userClass.getMethod("principal")
            val getRolesMethod = userClass.getMethod("roles")
            val getMetadataMethod = userClass.getMethod("metadata")
            val getFullNameMethod = userClass.getMethod("fullName")
            val getEmailMethod = userClass.getMethod("email")
            val getEnabledMethod = userClass.getMethod("enabled")

            val username = getUsernameMethod.invoke(userObject)
            val roles = getRolesMethod.invoke(userObject)
            val metadata = getMetadataMethod.invoke(userObject)
            val fullName = getFullNameMethod.invoke(userObject)
            val email = getEmailMethod.invoke(userObject)
            val enabled = getEnabledMethod.invoke(userObject)

            //String username, String[] roles, String fullName, String email, Map<String, Object> metadata, boolean enabled)
            val userClone = userClass.getConstructor(String::class.java, Array<String>::class.java, String::class.java, String::class.java, Map::class.java, Boolean::class.javaPrimitiveType)
                    .newInstance(username, roles, fullName, email, metadata, enabled)
            val clone = userClone.toString()
            if (authentication === customerAuthentication) {
                return false
            } else {
                println("clone user is $clone")
                return true
            }
        } catch (e: NoSuchMethodException) {
            e.printStackTrace()
        } catch (e: IllegalAccessException) {
            e.printStackTrace()
        } catch (e: InvocationTargetException) {
            e.printStackTrace()
        } catch (e: InstantiationException) {
            e.printStackTrace()
        }
        return null
    }
}
