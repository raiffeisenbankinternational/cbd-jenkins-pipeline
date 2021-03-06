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

def readConfig() {
    String config = sh(label: "Reading config from s3 bucket", returnStdout: true, script: """#!/bin/bash
           set -e
           aws s3 cp ${env.CONFIG_FILE_URL} -
           """)
    return parseJson(config)
}

def runTests(accountId, environmentNameUpper, deployTarget) {
    ansiColor('xterm') {
        sh(abel: "Running tests", script: """#!/bin/bash
            set -e
            
            export AWS_ACCOUNT_ID="${accountId}"
            export ENVIRONMENT_NAME="${environmentNameUpper}"
            export DEPLOY_TARGET="${deployTarget}"

            export TargetAccountId="${accountId}"
            export ServiceName="\${PROJECT_NAME}"
            export EnvironmentNameUpper="${environmentNameUpper}"

            export BUILD_DOCKER_IMAGE="${env.DOCKER_URL}/${DOCKER_ORG}/${JOB_BASE_NAME}:b${BUILD_ID}"
            if [[ -f Dockerfile.runtime ]]; then
               export BUILD_DOCKER_IMAGE="${env.DOCKER_URL}/${DOCKER_ORG}/${JOB_BASE_NAME}-runtime:b${BUILD_ID}"
            fi
 
            /dist/deploy.sh "glms-deploy-lib/run-all-tests" 
           """)
    }
}

def deploy(accountId, environmentNameUpper, deployTarget) {
    ansiColor('xterm') {
        sh """
            export AWS_ACCOUNT_ID="${accountId}"
            export ENVIRONMENT_NAME="${environmentNameUpper}"
            
            export TargetAccountId="${accountId}"
            export ServiceName="\${PROJECT_NAME}"
            export EnvironmentNameUpper="${environmentNameUpper}"
            
            export DEPLOY_TARGET="${deployTarget}"
 
            export BUILD_DOCKER_IMAGE="${env.DOCKER_URL}/${DOCKER_ORG}/${JOB_BASE_NAME}:b${BUILD_ID}"
            if [[ -f Dockerfile.runtime ]]; then
               export BUILD_DOCKER_IMAGE="${env.DOCKER_URL}/${DOCKER_ORG}/${JOB_BASE_NAME}-runtime:b${BUILD_ID}"
            fi

            /dist/deploy.sh
           """
    }
}

return this
