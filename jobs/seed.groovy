// If you want, you can define your seed job in the DSL and create it via the REST API.
// See https://github.com/sheehan/job-dsl-gradle-example#rest-api-runner

def branch = GIT_BRANCH
def project = GIT_REPO + "-" + GIT_PROJECT + "-" + branch

def job_name = project +'/' + 'seed'

folder(project){
    displayName(project)
}

listView(project) {
    description('OpenStack-Helm CI')
    jobs {
        name(project)
    }
    columns {
        status()
        weather()
        name()
        lastSuccess()
        lastFailure()
        lastDuration()
        buildButton()
    }
}

job(job_name) {
    parameters {
        stringParam('GIT_PROJECT', GIT_REPO + "/" + GIT_PROJECT)
        stringParam('GIT_BRANCH', branch)
    }
    scm {
        github("\$GIT_PROJECT", "\$GIT_BRANCH")
    }
    triggers {
        githubPush()
    }
    steps {
        dsl {
            external 'jobs/*_job.groovy'
            additionalClasspath 'src/main/groovy'
        }
    }
}