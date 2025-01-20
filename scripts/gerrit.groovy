def doCheckout() {
  checkout([$class: 'GitSCM',
   branches: [[name: 'master']],
   doGenerateSubmoduleConfigurations: false,
   extensions: [
    [
    $class: 'BuildChooserSetting',
       buildChooser: [$class: 'GerritTriggerBuildChooser']
    ],
    [
        $class: 'SubmoduleOption',
        disableSubmodules: false,
        parentCredentials: true,
        recursiveSubmodules: true,
        reference: '',
        trackingSubmodules: false
      ]],
   submoduleCfg: [],
   userRemoteConfigs: [[credentialsId: 'jenkins',
     refspec: '$GERRIT_REFSPEC',
     url: "${GIT_URL}"]]]
  )
}

def checkSubmitStatus(deployTarget) {
  if (deployTarget == "PROD" && env.GERRIT_CHANGE_ID) {
    withCredentials([sshUserPrivateKey(credentialsId: 'gerrit-ssh',
                                       keyFileVariable: 'SSHFILEPATH',
                                       passphraseVariable: 'SSHPASSPHRASE',
                                       usernameVariable: 'SSHUSERNAME')]) {
      String submitStatus = sh(label: 'Check submit status', returnStdout: true, script:
            """#!/bin/bash
            set -eu pipefail
            CHECK_GERRIT_BUILD="${env.GERRIT_CHANGE_SUBJECT}"
            if [[ "\${CHECK_GERRIT_BUILD}" == null ]] ; then
                echo "INFO: skipping Gerrit submit test"
                exit 0
            fi
            SUBMITTABLE=\$(curl -b ~/.gitcookie --fail -s \
                                             https://${GERRIT_URL}/a/changes/${GERRIT_CHANGE_ID}/revisions/${GERRIT_PATCHSET_REVISION}/actions \
                                                | tail -n +2 \\
                                                | jq -r '.submit.label')
            if [ "\${SUBMITTABLE}" != "Submit" ] ; then
                echo "ERROR: Change is not ready to submit!"
                exit 0
            else
                echo "INFO: Ready to submit, adding Patch-Set-Lock"
                curl -b ~/.gitcookie --fail -s https://${GERRIT_URL}/a/changes/${GERRIT_CHANGE_ID}/revisions/current/review \
                         --data '{"message": "Ready for production","labels":{"Patch-Set-Lock": 1}}' > /dev/null
            fi
            """).trim()

      echo "Status: ${submitStatus}"
      if (submitStatus.startsWith("INFO: Ready")) {
        sh(label: 'Ready to submit', script: "echo '${submitStatus}' && exit 0")
      } else {
        sh(label: 'Change is not ready to submit!', script: "echo -e '\\e[31m${submitStatus}\\e[0m' && exit 1")
      }
    }
  }
  else {
    print "DEV deployment"
  }
}

def submitChange() {
  withCredentials([sshUserPrivateKey(credentialsId: 'gerrit-ssh', keyFileVariable: 'SSHFILEPATH', passphraseVariable: 'SSHPASSPHRASE', usernameVariable: 'SSHUSERNAME')]) {
    if (env.GERRIT_CHANGE_ID) {
      sh(label: "Submit change", script: """#!/bin/bash
        CHECK_GERRIT_BUILD="${env.GERRIT_CHANGE_SUBJECT}"
        if [[ "\${CHECK_GERRIT_BUILD}" == null ]] ; then
          echo "INFO: skipping Gerrit submit"
          exit 0
        fi
        curl -b ~/.gitcookie --fail https://${GERRIT_URL}/a/changes/${GERRIT_CHANGE_ID}/revisions/current/review \
             --data '{"message": "Looking good","labels":{"Verified": 1}}'
             
        curl -b ~/.gitcookie --fail https://${GERRIT_URL}/a/changes/${GERRIT_CHANGE_ID}/submit \
             --data '{}'
      """)
    }
  }
}

def unlockPatchSet () {
  withCredentials([sshUserPrivateKey(credentialsId: 'gerrit-ssh', keyFileVariable: 'SSHFILEPATH', passphraseVariable: 'SSHPASSPHRASE', usernameVariable: 'SSHUSERNAME')]) {
    sh(label: "Unlock patchset", script: """#!/bin/bash
      CHECK_GERRIT_BUILD="${env.GERRIT_CHANGE_SUBJECT}"
      if [[ "\${CHECK_GERRIT_BUILD}" == null ]] ; then
        echo "INFO: skipping Gerrit unlockPatchSet"
        exit 0
      fi
      curl -b ~/.gitcookie --fail https://${GERRIT_URL}/a/changes/${GERRIT_CHANGE_ID}/revisions/current/review \
             --data '{"message": "Unlocking","labels":{"Patch-Set-Lock": 0}}'

    """)
  }
}


def maybePushToPublic() {
  if (env.PUBLIC_PUSH && env.PUBLIC_PUSH == "true") {
   withCredentials([usernamePassword(credentialsId: "github-http", usernameVariable: 'GIT_USERNAME', passwordVariable: 'GIT_PASSWORD')]) {
     sh(label: "Push to public", script: """#!/bin/bash
         set -e
         echo "Pushing to public \$last_commit_id"
         last_commit_id=\$(git log --format="%H" -n 1)
         git config credential.helper '!f() { sleep 1; echo "username=${GIT_USERNAME}"; echo "password=${GIT_PASSWORD}"; }; f'
         git push --force ${env.PUBLIC_URL} \$last_commit_id:refs/heads/master
     """)
     }
  }
}

def init() {
  sh 'id'
  sh 'ls -la'
  sh 'mkdir -p ansible'
  sh 'mkdir -p test'
  sh 'mkdir -p resources'
  sh 'mkdir -p schema'
  sh 'mkdir -p api'
  sh 'mkdir -p lib'
  sh 'mkdir -p ext'
  sh 'mkdir -p www'
  sh 'mkdir -p cert'
}

return this
