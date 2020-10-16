def checkJira() {
  withCredentials([usernamePassword(credentialsId: 'jira-http', passwordVariable: 'JIRA_PW', usernameVariable: 'JIRA_USER'),
  sshUserPrivateKey(credentialsId: 'gerrit-ssh', keyFileVariable: 'SSHFILEPATH', passphraseVariable: 'SSHPASSPHRASE', usernameVariable: 'SSHUSERNAME')]) {
    String gerritMessage = sh(label: 'Check errit commit message', returnStdout: true, script: """#!/bin/bash
       curl -b ~/.gitcookie --fail -v \
                       https://${GERRIT_URL}/a/changes/${GERRIT_CHANGE_ID}/revisions/${GERRIT_PATCHSET_REVISION}/commit \
                       | tail +2 \
                       | jq -r ".message"
       """)
    echo "Commit message ${gerritMessage}"
    JIRA_STATUS = sh(label: 'Check related Jira issue', returnStdout: true, script: """#!/bin/bash
            set -eu pipefail

            update_issue() {
              JIRA_STATUS="\${1}"
              ISSUE_SUM="\$(echo "\${JIRA_STATUS}" | jq -r ".fields.summary")"
              ISSUE_DESC="\$(echo "\${JIRA_STATUS}" | jq -r ".fields.description")"
              ISSUE_TYPE="\$(echo "\${JIRA_STATUS}" | jq -r ".fields.issuetype.name")"
              GERRIT_MESSAGE="${gerritMessage}"

              GERRIT_SUM_STEP1="\$(echo "\${GERRIT_MESSAGE/"[\${JIRA_ISSUE}] "/}" | head -n 1)"
              GERRIT_SUM_STEP2="\${GERRIT_SUM_STEP1//\\'/\\\\u0027}"
              GERRIT_SUM="\${GERRIT_SUM_STEP2//\\"/\\\\\\"}"
              GERRIT_DESC_STEP1="\$(echo "\${GERRIT_MESSAGE}" | tail -n +2)"
              GERRIT_DESC_STEP2="\$(printf '%q' "\$GERRIT_DESC_STEP1")"
              GERRIT_DESC_STEP3="\${GERRIT_DESC_STEP2//\\"/\\\\\\"}"
              GERRIT_DESC_STEP4="\${GERRIT_DESC_STEP3#\\\$\\\'}"
              GERRIT_DESC_STEP5="\${GERRIT_DESC_STEP4%?}"
              GERRIT_DESC="\${GERRIT_DESC_STEP5//\\\\\\'/\\\\u0027}"
              CONT_TYPE="Content-Type:application/json"

              if [ "\${ISSUE_TYPE^^}" == "BUG" ] || [ "\${ISSUE_TYPE^^}" ==  "STORY" ] ; then
                echo "INFO: Issue is a story or bug, skipping"
                exit 1
              elif [ "\${ISSUE_SUM}" !=  "\${GERRIT_SUM_STEP1}" ] && [ "\${ISSUE_DESC}" !=  "\${GERRIT_DESC_STEP1}" ] ; then
                echo "INFO: updating JIRA issue summary and description based on commit message."
                API_DATA='{ "fields": {"summary": "'"\${GERRIT_SUM}"'", "description": "'"\${GERRIT_DESC}"'" } }'
              elif [ "\${ISSUE_SUM}" !=  "\${GERRIT_SUM_STEP1}" ] ; then
                API_DATA='{ "fields": {"summary": "'"\${GERRIT_SUM}"'" } }'
                echo "INFO: updating JIRA issue summary based on commit message."
              elif [ "\${ISSUE_DESC}" !=  "\${GERRIT_DESC_STEP1}" ] ; then
                API_DATA='{ "fields": {"description": "'"\${GERRIT_DESC}"'" } }'
                echo "INFO: updating JIRA issue description based on commit message"
              else
                echo "INFO: Issue and commit message are the same, nothing to update"
                exit 0
              fi

              curl -f -D- -u "${JIRA_USER}:${JIRA_PW}" -X PUT --data "\${API_DATA}" -H "\${CONT_TYPE}" "${GLOBAL_JIRA_URL}/rest/api/2/issue/\${JIRA_ISSUE}"
            }

            CHECK_GERRIT_BUILD="${env.GERRIT_CHANGE_SUBJECT}"
            if [[ "\${CHECK_GERRIT_BUILD}" == null ]] ; then
              echo "INFO: skipping JIRA test"
              exit 0
            fi
            JIRA_PATTERN="^\\[[a-zA-Z0-9,\\.\\_\\-]+-[0-9]+\\]"
            JIRA_ISSUE="\$(echo "${env.GERRIT_CHANGE_SUBJECT}" | grep -o -E "\${JIRA_PATTERN}" | sed 's/^\\[\\(.*\\)\\]\$/\\1/')"
            if [ -z "\${JIRA_ISSUE}" ] ; then
                        echo "ERROR: Pattern does not match. Please use [JIRA-123] syntax in commit messages"
              exit 0
            fi
            JIRA_STATUS="\$(curl -s -u ${JIRA_USER}:${JIRA_PW} ${GLOBAL_JIRA_URL}/rest/api/2/issue/\${JIRA_ISSUE})"

            if get_status="\$(echo "\${JIRA_STATUS}" | jq -er ".fields.status.name")"; then
                if [ "\${get_status}" != "In Progress" ] ; then
                  echo "ERROR: Related Jira Issue (\${JIRA_ISSUE}) has to be In Progress, current status is \${get_status}"
                  exit 0
                else
                  echo "INFO: Related Jira Issue (\${JIRA_ISSUE}) status: \${get_status}"
                  update_issue "\${JIRA_STATUS}"
                  exit 0
                fi
            elif get_error="\$(echo "\${JIRA_STATUS}" | jq -er ".errorMessages[]")"; then
              echo "ERROR: Commit message error related to \${JIRA_ISSUE}: \$get_error"
              exit 0
            else
              echo "ERROR: Unknown error during Jira issue ckeck"
            exit 0
            fi
            """).trim()
        if (JIRA_STATUS.startsWith("ERROR: Pattern")) {
            sh(label: 'Jira ticket pattern does not match [JIRA-123]', script: "echo -e '\\e[31m${JIRA_STATUS}\\e[0m' && exit 1")
        } else if (JIRA_STATUS.startsWith("ERROR: Related")) {
            sh script: "echo -e '\\e[31m${JIRA_STATUS}\\e[0m' && exit 1", label: 'Jira ticket not In-Progress state'
        } else if (JIRA_STATUS.startsWith("INFO:")) {
            sh(label: 'Jira ticket OK', script: "echo '${JIRA_STATUS}' && exit 0")
        } else {
            sh(label: 'Unknown jira error', script: "echo -e '\\e[31m${JIRA_STATUS}\\e[0m' && exit 1")
        } 

    }
}

def close() {
    withCredentials([usernamePassword(credentialsId: 'jira-http', passwordVariable: 'JIRA_PW', usernameVariable: 'JIRA_USER')]) {
      sh (label: 'Close related Jira issue', script: """#!/bin/bash
        set -xeu pipefail
        CHECK_GERRIT_BUILD="${env.GERRIT_CHANGE_SUBJECT}"
        if [[ "\${CHECK_GERRIT_BUILD}" == null ]] ; then
          echo "INFO: skipping JIRA test"
          exit 0
        fi
        JIRA_PATTERN="^\\[[a-zA-Z0-9,\\.\\_\\-]+-[0-9]+\\]"
        JIRA_ISSUE="\$(echo "${env.GERRIT_CHANGE_SUBJECT}" | grep -o -E "\${JIRA_PATTERN}" | sed 's/^\\[\\(.*\\)\\]\$/\\1/')"
        DONE_WORKFLOW_ID="\$(curl -s -u ${JIRA_USER}:${JIRA_PW} ${GLOBAL_JIRA_URL}/rest/api/2/issue/\${JIRA_ISSUE}/transitions?transitionId | \
        jq -re '.transitions[] | select(.name=="Done") | .id')"
        curl -D- -u ${JIRA_USER}:${JIRA_PW} -X POST --data '{ "transition": { "id": '"\${DONE_WORKFLOW_ID}"' } }'  -H "Content-Type: application/json" \
        ${GLOBAL_JIRA_URL}/rest/api/2/issue/\${JIRA_ISSUE}/transitions?transitionId
      """)
    }
}

return this

