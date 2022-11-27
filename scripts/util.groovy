import com.cloudbees.groovy.cps.NonCPS
import hudson.model.Item
import hudson.model.Job
import hudson.model.ParametersDefinitionProperty
import jenkins.model.Jenkins


@NonCPS
def static getItems() {
    return Jenkins.getInstanceOrNull()
            .getAllItems(Item.class)
            .collect { v -> v.getFullName() }
}

@NonCPS
def static getGitUrl(String name) {
    Job job = Jenkins.getInstanceOrNull().getItemByFullName(name, Job.class)
    ParametersDefinitionProperty paramDefProp = (ParametersDefinitionProperty) job.getProperty(ParametersDefinitionProperty.class)

    /* Scan for all parameter with an associated default values */
    return paramDefProp.getParameterDefinitions()
            .find { v -> v.getName() == "GIT_URL" }
            .getDefaultParameterValue()
            .getValue()
            .toString()
}


def checkoutAllRepos() {
    getItems().each { name ->
      echo("Analyzing : " + name)
        if (name.startsWith("glms-service/")) {
          dir('services/' + name) {
              String gitUrl = getGitUrl(name)
              echo("Checkinf out: " + gitUrl)
              checkout([$class                           : 'GitSCM',
                        branches                         : [[name: 'master']],
                        doGenerateSubmoduleConfigurations: false,
                        extensions                       : [],
                        submoduleCfg                     : [],
                        userRemoteConfigs                : [[credentialsId: 'jenkins',
                        depth: 1,
                        url: gitUrl]]]
              )
           }
        }
    }
}

return this;
