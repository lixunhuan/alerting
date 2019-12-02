package com.amazon.opendistroforelasticsearch.alerting.core.auth

import org.elasticsearch.Version
import org.elasticsearch.common.util.concurrent.ThreadContext
import java.lang.reflect.InvocationTargetException
import java.lang.reflect.Method
import java.util.HashMap
import org.apache.logging.log4j.LogManager
import kotlinx.coroutines.sync.*

class AuthCenter(private val tContext: ThreadContext) {
    companion object {
        private var target: AuthCenter? = null
        private val logger = LogManager.getLogger(AuthCenter)
        private const val XPACK_SECURITY_AUTH_HEADER = "_xpack_security_authentication"
        private const val USER_MUTEX_LOCK_NAME = "user_mutex"
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
            val user = target?.getAuthenticationGetUserMethod()?.invoke(authentication)
            return FakeXPackClass.FakeUser.getPrincipalMethod().invoke(user) as String
        }

        @Synchronized
        fun getCurrentUserRole(): Array<String>? {
            val authentication: Any? = target!!.tContext.getTransient<Any>(XPACK_SECURITY_AUTH_HEADER) ?: return null
            val user = target?.getAuthenticationGetUserMethod()?.invoke(authentication)
            return FakeXPackClass.FakeUser.getRolesMethod().invoke(user) as Array<String>
        }

        private var userRoles: ThreadLocal<Any> = ThreadLocal()
        private fun makeUser(username: String, roles: Array<String>): Any {
            val userClass = FakeXPackClass.FakeUser.loadClass()
            return userClass.getConstructor(String::class.java, Array<String>::class.java, String::class.java, String::class.java, Map::class.java, Boolean::class.javaPrimitiveType)
                    .newInstance(username, roles, username, "", null, true)
        }

        private fun makeAuthentication(user: Any): Any {
            val realm = FakeXPackClass.FakeAuthenticationRealm.newInstance("__attach", "__attach", "nodeName")
            // val typeClass = FakeXPackClass.FakeAuthenticationType.loadClass()
            val internalType = FakeXPackClass.FakeAuthenticationType.internal
            return FakeXPackClass.FakeAuthentication.newInstance(user, realm, null, Version.CURRENT, internalType!!, emptyMap<Any, Any>())
        }

        private val localUserMutex: ThreadLocal<Mutex> = ThreadLocal()
        private fun getLocalUserMutex(): Mutex {
            var m = localUserMutex.get()
            if (m == null) {
                m = Mutex()
                localUserMutex.set(m)
            }
            return m
        }

        fun setThreadLocalUser(user: String, roles: Array<String>) {
            logger.info("${Thread.currentThread().id} Try lock ${getLocalUserMutex()} for user $user")
            getLocalUserMutex().tryLock(USER_MUTEX_LOCK_NAME)
            logger.info("${Thread.currentThread().id} ${getLocalUserMutex()} is locked for user $user")
            userRoles.set(makeUser(user, roles))
        }

        fun cleanThreadLocalUser() {
            userRoles.remove()

            if (getLocalUserMutex().isLocked) {
                logger.info("${Thread.currentThread().id} ${getLocalUserMutex()} is locked, try unlock")
                getLocalUserMutex().unlock(USER_MUTEX_LOCK_NAME)
            } else {
                logger.info("${Thread.currentThread().id} ${getLocalUserMutex()} is not locked, do nothing")
            }
        }

        @Synchronized
        fun <T> execWithElasticUser(callback: () -> T): T {
            val needToRecoverCurrentAuthentication =
                    target!!.tContext.getTransient<Any>(XPACK_SECURITY_AUTH_HEADER) != null

            if (needToRecoverCurrentAuthentication) {
                val recover = ExtendThreadContextManager.clean(target!!.tContext, XPACK_SECURITY_AUTH_HEADER)
                //println("replace with mariya authentication")
                target?.setElasticUserToContext()
                val result = callback()
                //println("recover original authentication")
                recover.recover()
                return result
            } else {
                //println("insert with mariya authentication")
                target?.setElasticUserToContext()
                val result = callback()
                ExtendThreadContextManager.clean(target!!.tContext, XPACK_SECURITY_AUTH_HEADER)
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

            authentication = makeAuthentication(user)

        }
        return authentication
    }

    private var writeToContextMethod: Method? = null
    @Synchronized
    fun getElasticUserAuthenticationWriteToContextMethod(): Method {
        if (writeToContextMethod == null) {
            writeToContextMethod = buildElasticUserAuthentication()!!.javaClass.getMethod("writeToContext", ThreadContext::class.java)
        }
        return writeToContextMethod!!
    }

    private var authenticationGetUserMethod: Method? = null
    @Synchronized
    fun getAuthenticationGetUserMethod(): Method {
        if (authenticationGetUserMethod == null) {
            authenticationGetUserMethod = buildElasticUserAuthentication()!!.javaClass.getMethod("getUser")
        }
        return authenticationGetUserMethod!!
    }

    fun setElasticUserToContext() {
        if (tContext.getTransient<Any>(XPACK_SECURITY_AUTH_HEADER) == null) {
            //println("${Thread.currentThread().name} : generate elastic user")
            val user = userRoles.get()
            if (user == null) {
                getElasticUserAuthenticationWriteToContextMethod()
                        .invoke(authentication, tContext)
            } else {
                getElasticUserAuthenticationWriteToContextMethod()
                        .invoke(makeAuthentication(user), tContext)
            }
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
