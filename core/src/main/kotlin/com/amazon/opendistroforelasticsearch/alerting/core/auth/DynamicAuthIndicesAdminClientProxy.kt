package com.amazon.opendistroforelasticsearch.alerting.core.auth


import java.lang.reflect.InvocationHandler
import java.lang.reflect.Method

class DynamicAuthIndicesAdminClientProxy(private val target: Any) : InvocationHandler {

    @Throws(Throwable::class)
    override fun invoke(proxy: Any, method: Method, args: Array<Any>): Any? {
        this.traceLog(method.name)
        if (args == null){
            when (method.name) {
                "exist", "create", "rolloversIndex", "putMapping" ->
                    //todo make context
                    return AuthCenter.execWithElasticUser { method.invoke(target) }
                else -> return method.invoke(target)
            }
        }else{
            when (method.name) {
                "exist", "create", "rolloversIndex", "putMapping" ->
                    //todo make context
                    return AuthCenter.execWithElasticUser { method.invoke(target, *args) }
                else -> return method.invoke(target, *args)
            }
        }
    }

    companion object {
        private var callerMap = HashMap<String,String>()
    }
    fun traceLog(method:String){
        var stackTraceElement:StackTraceElement? = null
        for (stack in Thread.currentThread().stackTrace){
            var targetName = stack.className
            if (targetName == this.javaClass.name){
                continue
            }
            if (stackTraceElement == null && targetName.substring(0,10) == "com.amazon"){
                stackTraceElement = stack
            }
            if (stack.methodName == "runWithElasticUser"){
                //this method called by runWithElasticUser
                return
            }
        }
        if (stackTraceElement != null){
            var targetName = stackTraceElement.className
            if (callerMap[targetName+stackTraceElement.lineNumber] == null ){
                callerMap[targetName+stackTraceElement.lineNumber] = method
                println("$targetName line ${stackTraceElement.lineNumber} call $method")
            }
        }
    }
}
