import com.cloudbees.groovy.cps.NonCPS
import jenkins.model.Jenkins
import org.jenkinsci.plugins.workflow.job.WorkflowJob

@NonCPS
def static getItems() {
    return Jenkins.getInstanceOrNull()
            .getAllItems(WorkflowJob.class)
            .collect { v ->
                v.getDefinition()
                v.getDefinition().getProperties().get("scriptPath")
            }
}


getItems().each { p -> println p }

return this
