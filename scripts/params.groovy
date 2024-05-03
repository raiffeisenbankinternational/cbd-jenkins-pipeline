properties([
  parameters([
    booleanParam(name: 'RUN_SETUP',
                 defaultValue: 'false',
                 description: 'Used only when project is being configured'),
    stringParam(name: 'GIT_URL',
            defaultValue: env.GIT_URL,
            description: 'Git repo URL'),
    stringParam(name: 'GERRIT_REFSPEC',
            defaultValue: env.GIT_URL,
            description: 'Refspec of change'),
    stringParam(name: 'GERRIT_BRANCH',
            defaultValue: env.GIT_URL,
            description: 'Branch or commit-id'),
    extendedChoice(
            name: 'DEPLOY_TO_FRIEND',
            type: 'PT_SINGLE_SELECT',
            description: 'Select to deploy to friends environment',
            script: """
                def jsonSlurper = new groovy.json.JsonSlurper()
                def devAccounts = jsonSlurper.parse(new File("/var/lib/jenkins/accounts.json"))
                return [""] + devAccounts.devAccounts.collect{ it.name }
            """
    )

  ])
])

return this