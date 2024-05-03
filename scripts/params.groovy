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
    activeChoice(
            name: 'DEPLOY_TO_FRIEND',
            choiceType: 'PT_SINGLE_SELECT',
            description: 'Select to deploy to friends environment',
            script: [
                    $class: 'GroovyScript',
                    scriptlerScriptId:'Environments.groovy',
                    script: [
                            classpath: [],
                            sandbox: false,
                            script: 'return [""] + new groovy.json.JsonSlurper().parse(new File("/var/lib/jenkins/accounts.json")).devAccounts.collect{ it.name }'
                    ]
            ]

    ),
    booleanParam(name: 'ROLLOUT_TO_ALL',
            defaultValue: 'true',
            description: 'After merge deploy to all dev environemnts'),
    booleanParam(name: 'DEPLOY_TO_PROD',
            defaultValue: 'false',
            description: 'Deploy master to production'),
    booleanParam(name: 'DEPLOY_ALL_DEV',
            defaultValue: 'false',
            description: 'Deploy master to all dev env'),

  ])
])

return this