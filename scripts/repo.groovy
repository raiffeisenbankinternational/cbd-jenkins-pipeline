def upload(String file, String pomFile, String artifactId, String groupId, String version, String repo, String credentials) {
    withCredentials([usernamePassword(credentialsId: credentials, usernameVariable: 'USERNAME', passwordVariable: 'PASSWORD')]) {
        ansiColor('xterm') {
            sh(label: "Upload file", script: """
               VERSION=${version}
               ARTIFACT_ID=${artifactId}
               GROUP_ID=${groupId}
               FILE=${file}

               ARTIFACT_NAME=\$(basename \${FILE})
               PACKAGING=\${ARTIFACT_NAME##*.}
               echo "Deploying: \${GROUP_ID}:\${ARTIFACT_ID} on file \${FILE} packaged as \${PACKAGING}  with version: \${VERSION}"
               mvn deploy:deploy-file \
                 -DgroupId=\${GROUP_ID} \
                 -DartifactIda=\${ARTIFACT_NAME} \
                 -Dversion=\${VERSION} \
                 -Dpackaging=\${PACKAGING} \
                 -Dfile=\${FILE} \
                 -DrepositoryId=artifacts \
                 -DgeneratePom=false \
                 -DpomFile=${pomFile} \
                 -Dartifacts.username=\${USERNAME} -Dartifacts.password=\${PASSWORD} \
                 -Durl=${repo}/
            """)
        }
    }
}

def deploy(accountId, environmentNameUpper, deployTarget) {
    ansiColor('xterm') {
        sh(label: "Deploy using container", script: """
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
           """)
    }
}


def uploadJar(String file, deployTarget) {
    String name = file.substring(0, file.lastIndexOf('.'));
    String version = name.substring(name.lastIndexOf('-') + 1);
    String artifactId = name.substring(0, name.lastIndexOf('-'));
    String groupId = env.GLOBAL_GROUP_ID;
    String repo = "${env.GLOBAL_REPOSITORY_DEV_URL}";
    String credentials = "artifact-deploy-dev-http"
    if (deployTarget == "PROD") {
        repo = "${env.GLOBAL_REPOSITORY_PROD_URL}";
        credentials = "artifact-deploy-http"
    }
    upload("/dist/release-libs/" + file,
            "/dist/release-libs/" + file + ".pom.xml",
            artifactId,
            groupId,
            version,
            repo,
            credentials)
}

def uploadLibs(String deployTarget, Boolean required = true) {
    echo "Checking for libs to push"
    String fileList = sh(script: "ls   '/dist/release-libs'", returnStdout: true)
    String FILES_LIST = fileList.trim()
    echo "FILES_LIST : ${FILES_LIST}"
    if (!FILES_LIST?.trim() && required) {
       sh(label: "No libs found to deploy!", script: "echo 'No libs found to deploy!!' && exit 1", returnStdout: true)
    }
    for (String file : FILES_LIST.split("\\r?\\n")) {
        echo "Listing: ${file}"
        if (file.endsWith(".jar")) {
            echo "Found file: ${file}"
            uploadJar(file, deployTarget)
        }
    }}
return this;
