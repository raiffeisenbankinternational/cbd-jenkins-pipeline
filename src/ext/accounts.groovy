import groovy.json.JsonSlurper

def jsonSlurper = new JsonSlurper()
def devAccounts = jsonSlurper.parse(new File("/var/lib/jenkins/accounts.json"))
return [""] + devAccounts.devAccounts.collect{ it.name }