import com.cloudbees.groovy.cps.NonCPS
import groovy.json.JsonSlurperClassic

@NonCPS
static
def parseJson(jsonString) {
    // Would like to use readJSON step, but it requires a context, even for parsing just text.
    Map<?, ?> lazyMap = (Map<?, ?>) new JsonSlurperClassic().parseText(jsonString)

    // JsonSlurper returns a non-serializable LazyMap, so copy it into a regular map before returning
    Map<?, ?> m = [:]
    m.putAll(lazyMap)
    return m
}


return this
