# Jenkins setup
Following environment variables are required in jenkins global configuration

``` 
DOCKER_URL=docker.example.com
DOCKER_PUSH_URL=docker-push.example.com
GERRIT_USER=jenkins
GERRIT_EMAIL=jenkins@example.com
GERRIT_URL=gerrit.example.com
GLOBAL_GROUP_ID=com.example.project
GLOBAL_JIRA_URL=https://jira.example.com
GLOBAL_PROPERTIES_AWS_ACCOUNTS={ "accounts": [ { "id": "1", "owner": "first1.last1@example.com", "name": "first1.last1", "environmentName": "DEV01", "accountId": "12345678912" }, { "id": "2", "owner": "first2.last2@example.com", "name": "first2.last2", "environmentName": "DEV02", "accountId": "23456789123" } ] }
GLOBAL_PROPERTIES_DOCKER_BUILD_ARGS=--build-arg ARTIFACT_VERSION=${ARTIFACT_VERSION:-} --build-arg ARTIFACT_ID=${ARTIFACT_ID:-} --build-arg SOURCE_URL=${SOURCE_URL:-} --build-arg BUILD_ID=${BUILD_ID} --ssh default --progress plain
GLOBAL_PROPERTIES_PROD_AWS_ACCOUNT=456789123456
GLOBAL_PROPERTIES_TEST_AWS_ACCOUNT=7891234567891
GLOBAL_REPOSITORY_DEV_URL=https://nexus.example.com/dev
GLOBAL_REPOSITORY_PROD_URL=https://nexus.example.com/prod
GLOBAL_GROUP_ID=com.example

```

#Secrets
Following secrets are required:
```
gerrit-ssh # private key for non-interractive user in gerrit
jira-http # username/password for jira (In cloud is API key)
artifact-deploy-dev-http # username/password for deploying artifacts (i.e. nexus)
artifact-deploy-prod-http # username/password for deploying artifacts (i.e. nexus)
docker-push-http # username/password for pushing docker images to registry
gerrit-http # username/password for non-interractive user in gerrit
```

# Development

http://tdongsi.github.io/blog/2018/02/09/intellij-setup-for-jenkins-shared-library-development/

# Change based development

Trunk base development is very popular development model.
It is often misinterpreted and usually far hard to enforce. 

Starting from Trunk based development we want to strickly 
define development strategy that will help us solve our business
problems. 

Change based development (`CBD`) is subset of Trunk based development
All items of `CBD` are also Trunk based development. 
Main idea is to enforce pipeline that will eliminate gray area and 
return focus on automation and inovation instead of branching model. 

## Rule 1 - merge after production deployment
We are still collaborating on trunk branch. 
Merges to that branch happen after deployment to production
Change is always 1 commit ahead of previous trunk version

## Rule 2 - n+2 environments
Every engineer needs to have his own private isolated environment
There should be only 2 additional environments TEST and PROD

## Rule 3 - Deployments to production are sequential  
All deployments to production go one by one (within one bounded context)
Deployments are done by means of canary or blue/green release
Each deployment measures the most important metrics

No more deployments before sprint end because the expected deployment duration 
is so long that we cannot deploy the whole solution at the day before the release.

Enforce people to continuously release their solution to prevent long living branches.
Source code repository has to assess pull request size and basically prioritize smaller 
requests for deployment.

## Rule 4 - Direct link with issue tracker
CI/CD pipeline (Not source code repository) is connected with issue tracker
Issue validation is done on pipeline to make it more flexible and extendable.
Ensures clean issue tracker because it is first class citizen for the engineer.

No need for scrum master to manage tickets, engineers are forced to take care of tickets.

No reuse of tickets



# CBD PIPELINE

We use Jenkins and Gerrit for building our code and deploying the artifacts to various environments in AWS (developer, test and production accounts).
## Requirements
* Jenkins and Gerrit accounts
* SSH public key, added to the Gerrit profile
* Git client

## Usage

### Checking out code and creating changes

**It is recommended to use git client from terminal.** For cloning a repository, go to that repository in Gerrit (Browse/Repositories), then in the Download section select SSH, copy the "Clone with commit-msg hook" line and paste it to the terminal. Note: your key has to be loaded and you have to be in the folder which you want to contain your projects.

We only work on master branch, after adding our modifications, we need to commit (with the pattern "[GLMS-123] only example", referring to the related Jira ticket) and push to "HEAD:refs/for/master" ref:

```
git add example.cljs
git commit -m "[GLMS-123] only example"
git push origin HEAD:refs/for/master
```

Each new commit uploaded by the git push client will be converted into a change record on the server. Because one task should be accomplished with one change record, if modifications to an existing change record are needed, just use commit --amend:

```
git add example.cljs
git commit --amend
git push origin HEAD:refs/for/master
```

Note: with amend you can also change th commit message, but if this is not necessary, then you can use the "--no-edit" flag.

```
git commit --amend --no-edit
```

### Reviewing changes, submitting

After a change is created in Gerrit, it triggers a corresponding job in Jenkins. This job builds and deploys the code to the developer AWS account which is assigned to you. If the job was successful, it sets the Verified label to +1 and if it failed to -1. A +1 Verified label is necessary for the a production deployment.

If you feel your code is ready, it needs to be assigned to someone to review it. This can be achieved by clicking the "ADD REVIEWER" button in the change record. The reviewer can recommend additional modifications, or ask someone else as well to review the code by adding +1 value to the "Code-Review" label. If the reviewer gives -2 for "Code-Review", then the change can not be deployed to production.

If the reviewer gives +2 for "Code-Review", Gerrit will trigger a production deployment in Jenkins. The deployment checks first if there is a corresponding Jira ticket with status "in Progress" based on the commit message, and whether the change is ready to be submitted. If either of these checks fails, it will stop the deployment. Otherwise it will lock the code by adding a +1 value to the "Patch-Set-Lock" label, so that no modifications of the code are possible anymore for the duration of the production deployment. After the change has been successfully deployed to production, Jenkins submits the change to master and closes the related Jira ticket.

| Code-Review value | Interpretation
| :---------------: | --------------
| -2                | This shall not be merged. **It blocks production deployment**
| -1                | I would prefer this is not merged as is
| 0                 | No score
| +1                | Looks good to me, but someone else must approve
| +2                | Looks good to me, approved. **It triggers production deployment**

In case that the production deployment fails, nothing will be submitted, but Jenkins will give -1 value to Verified label and unlock the code. This indicates, that something failed during the production deployment, which wasn't failing during the dev deployment, meaníng that investigation is necessary.

### Commit messages are synced with referenced Jira issue

For the sake of transparency, Jenkins updates the referenced Jira issue based on the commit message. The first line of the commit message will be put as the summary of the issue (but without the Jira issue number), and the rest will be the description.

For example having the following commit message:
```
[GLMS-123] Add example feature
- makes the product better
- resolves "example" bug
- enhance user experience

Change-Id: I961ffae7519c7f0e4fab0a26efc262ac04218983
```

Will result the following Jira fields in issue "GLMS-123":

**Jira Summary:**
> Add example feature

**Jira Description:**
> - makes the product better
> - resolves "example" bug
>  enhance user experience
>
> Change-Id: I961ffae7519c7f0e4fab0a26efc262ac04218983

Every time the commit message changes, Jenkins will update the referenced Jira issue during a deployment. If you change manually the Jira issue, Jenkins will detect the difference and will overwrite the manual changes.

Note: Gerrit adds automatically the Change-Id to the last line of the commit message after the first commit. Please, do not remove or change it!

### Specifying environment with Gerrit Topic

You have many options to deploy to specific environments (AWS accounts) in Jenkins using the "Build with Parameters" option like deploy to production, deploy to all developer environments or deploy to a friend. You can even specify the Gerrit change request you want to deploy with the GERRIT_REFSPEC and GERRIT_BRANCH parameters.

In addition to these, it is possible to set the environment with "git push" command itself. This could be useful in scenarios when it's important to execute something in an account which is different than the account belonging to the owner of the change request. One example could be when a test engineer needs to test some features which are deployed to another developer's account. To achieve this, you can set the topic in the "git push" command:

```
git push origin HEAD:refs/for/master%topic=env/DEV01
```

The value of the topic in this example is "env/DEV01". The "env/" prefix is needed to let Jenkins identify that this topic is used for specifying the environment, so this will be deployed to "DEV01" AWS account. You can also see the topic in the change request's "Topic" field. Once you set the topic field, it will remain there untill you change it with the next push or by manually editing in the change request page.

### Checking errors in Jenkins

You can check why a job failed in Jenkins by viewing the corresponding Jenkins job. You can see an overview of the deployment immediately in the Stage View. If your job failed, because of a problem of a Jira ticket or it wasn't ready to submit, you should see that the Build stage has failed. Moving the mouse over the stage should display the error. If you need more details, find the failed job in build history, click the error and select console output.

For more information how to use Gerrit and Jenkins check out the following guides:
* [Jenkins User Documentation](https://jenkins.io/doc/)
* [Gerrit User Guide](https://gerrit-review.googlesource.com/Documentation/intro-user.html)
