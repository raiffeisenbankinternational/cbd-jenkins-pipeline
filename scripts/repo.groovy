def upload(String file, String pomFile, String artifactId, String groupId, String version, String repo, String repoId, String credentials) {
    ansiColor('xterm') {
            sh(label: "Upload file", script: """
               VERSION=${version}
               ARTIFACT_ID=${artifactId}
               GROUP_ID=${groupId}
               FILE=${file}
               ARTIFACT_NAME=\$(basename \${FILE})
               PACKAGING=\${ARTIFACT_NAME##*.}
               echo "Adding licence information: "
               sed "s|<licenses>|  <license>\n    <name>Your License Name</name>\n    <url>Your License URL</url>\n    <distribution>repo</distribution>\n  </license>|" pom.xml > temp.pom.xml
               sed -i 's/<\\/project>/<license><name>Apache-2.0<\\/name><url>https:\\/\\/www.apache.org\\/licenses\\/LICENSE-2.0.txt<\\/url><\\/license><\\/project>/g' pom.xml
               cat pom.xml
               echo "Deploying: \${GROUP_ID}:\${ARTIFACT_ID} on file \${FILE} packaged as \${PACKAGING}  with version: \${VERSION}"
               mvn deploy:deploy-file \
                 -DgroupId=\${GROUP_ID} \
                 -DartifactId=\${ARTIFACT_ID} \
                 -Dversion=\${VERSION} \
                 -Dpackaging=\${PACKAGING} \
                 -Dfile=\${FILE} \
                 -DrepositoryId=${repoId} \
                 -DgeneratePom=false \
                 -DpomFile=${pomFile} \
                 -Durl=${repo}/
            """)
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
            /dist/ext/deploy.sh
           """)
    }
}


def uploadJar(String fileName, deployTarget, String buildId) {
    String file = fileName[0..-9]
    echo "Uploading file $file"

    String name = file.substring(0, file.lastIndexOf('.'));
    echo "Name: $name"

    String version = fileName.substring(fileName.lastIndexOf('-') + 1);
    echo "Part version $version"
    version = (version =~ /(^[\d\.b]+)[\.]+/).findAll()[0][1]
    echo "Pre-last version $version"
    number = version.replace("b", "").substring(0, version.lastIndexOf('.'));
    version = number + "." + buildId
    echo "Final version $version"


    String artifactId = name.substring(0, name.lastIndexOf('-'));
    String groupId = env.ARTIFACT_DEV_ORG;
    String repo = "${env.GLOBAL_REPOSITORY_DEV_URL}";
    String repoId = "dev"
    String credentials = "artifact-deploy-dev-http"
    if (deployTarget == "PROD") {
        if (env.PUBLIC_RELEASE && env.PUBLIC_RELEASE == "true") {
            repo = "${env.GLOBAL_REPOSITORY_PUBLIC_URL}";
            repoId = "clojars"
            credentials = "artifact-deploy-public-http"
            groupId = "${env.ARTIFACT_PUBLIC_ORG}";
        } else {
            repo = "${env.GLOBAL_REPOSITORY_PROD_URL}";
            repoId = "prod"
            credentials = "artifact-deploy-http"
            groupId = "${env.ARTIFACT_ORG}";
        }
    }

    upload("/dist/release-libs/" + file,
            "/dist/release-libs/" + file + ".pom.xml",
            artifactId,
            groupId,
            version,
            repo,
            repoId,
            credentials)
}

def uploadLibs(String deployTarget, Boolean required = true, String buildId) {
    echo "Checking for libs to push"
    String fileList = sh(script: "ls   '/dist/release-libs'", returnStdout: true)
    String FILES_LIST = fileList.trim()
    echo "FILES_LIST : ${FILES_LIST}"
    if (!FILES_LIST?.trim() && required) {
        sh(label: "No libs found to deploy!", script: "echo 'No libs found to deploy!!' && exit 1", returnStdout: true)
    }
    for (String file : FILES_LIST.split("\\r?\\n")) {
        echo "Listing: ${file}"
        if (file.endsWith(".pom.xml") && ! file.endsWith(".jar.pom.xml")) {
          String stripped = file[0..-9] + ".jar.pom.xml"
          sh(label: "Fallback for broken deployment", script: "a=/dist/release-libs/$file; mv \$a /dist/release-libs/$stripped")
        }
    }
    fileList = sh(script: "ls   '/dist/release-libs'", returnStdout: true)
    FILES_LIST = fileList.trim()
    echo "FILES_LIST : ${FILES_LIST}"
    for (String file : FILES_LIST.split("\\r?\\n")) {
        echo "Listing: ${file}"
        if (file.endsWith(".pom.xml")) {
            echo "Found file: ${file}"
            uploadJar(file, deployTarget, buildId)
        }
    }
}
return this;
