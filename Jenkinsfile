String version = env.BRANCH_NAME
Boolean isRelease = version ==~ /v\d+\.\d+\.\d+.*/
Boolean isPR = env.CHANGE_ID != null

/*
 * Loops until the endpoint is up (true) or down (false), for 20 attempts maximum
 */
def wait(endpoint, up, attempt = 1) {
    if (attempt > 20) {
        error("Invalid response from $endpoint after 20 attempts")
    }
    def response = httpRequest url: endpoint, validResponseCodes: '100:599' // prevents an exception to be thrown on 500 codes
    if (up && response.status == 200) {
        echo "$endpoint is up"
        return
    } else if (!up && response.status == 503) {
        echo "$endpoint is down"
        return
    } else {
        sleep 10
        wait(endpoint, up, attempt + 1)
    }
}

pipeline {
    agent { label 'slave-sbt' }
    options {
        timeout(time: 30, unit: 'MINUTES') 
    }
    environment {
        ENDPOINT = sh(script: 'oc env statefulset/iam -n bbp-nexus-dev --list | grep SERVICE_DESCRIPTION_URI', returnStdout: true).split('=')[1].trim()
    }
    stages {
        stage("Review") {
            when {
                expression { isPR }
            }
            parallel {
                stage("Static Analysis") {
                    steps {
                        node("slave-sbt") {
                            checkout scm
                            sh 'sbt clean scalafmtCheck scalafmtSbtCheck test:scalafmtCheck compile test:compile scapegoat'
                        }
                    }
                }
                stage("Tests & Coverage") {
                    steps {
                        node("slave-sbt") {
                            checkout scm
                            sh "sbt clean coverage test coverageReport coverageAggregate"
                            sh "curl -s https://codecov.io/bash >> ./coverage.sh"
                            sh "bash ./coverage.sh -t `oc get secrets codecov-secret --template='{{.data.nexus_iam}}' | base64 -d`"
                        }
                    }
                }
            }
        }
        stage("Build & Publish Artifacts") {
            when {
                expression { !isPR }
            }
            steps {
                checkout scm
                sh 'sbt releaseEarly universal:packageZipTarball'
                stash name: "service", includes: "target/universal/iam-*.tgz"
            }
        }
        stage("Build Image") {
            when {
                expression { !isPR }
            }
            steps {
                unstash name: "service"
                sh "mv target/universal/iam-*.tgz ./iam.tgz"
                sh "oc start-build iam-build --from-file=iam.tgz --wait"
            }
        }
        stage("Redeploy & Test") {
            when {
                expression { !isPR && !isRelease }
            }
            steps {
                sh "oc scale statefulset iam --replicas=0 --namespace=bbp-nexus-dev"
                sleep 10
                wait(ENDPOINT, false)
                sh "oc scale statefulset iam --replicas=1 --namespace=bbp-nexus-dev"
                sleep 120 // service readiness delay is set to 2 minutes
                openshiftVerifyService namespace: 'bbp-nexus-dev', svcName: 'iam', verbose: 'false'
                wait(ENDPOINT, true)
                build job: 'nexus/nexus-tests/master', parameters: [booleanParam(name: 'run', value: true)], wait: true
            }
        }
        stage("Tag Images") {
            when {
                expression { isRelease }
            }
            steps {
                openshiftTag srcStream: 'iam', srcTag: 'latest', destStream: 'iam', destTag: version.substring(1), verbose: 'false'
            }
        }
        stage("Push to Docker Hub") {
            when {
                expression { isRelease }
            }
            steps {
                unstash name: "service"
                sh "mv target/universal/iam-*.tgz ./iam.tgz"
                sh "oc start-build nexus-iam-build --from-file=iam.tgz --wait"
            }
        }
        stage("Report Coverage") {
            when {
                expression { !isPR }
            }
            steps {
                checkout scm
                sh "sbt clean coverage test coverageReport coverageAggregate"
                sh "curl -s https://codecov.io/bash >> ./coverage.sh"
                sh "bash ./coverage.sh -t `oc get secrets codecov-secret --template='{{.data.nexus_iam}}' | base64 -d`"
            }
            post {
                always {
                    junit 'target/test-reports/TEST*.xml'
                }
            }
        }
    }
}