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
                stash name: "service", includes: "modules/service/target/universal/iam-service-*.tgz"
                stash name: "oidc-bbp", includes: "modules/oidc/bbp/target/universal/iam-bbp-*.tgz"
                stash name: "oidc-hbp", includes: "modules/oidc/hbp/target/universal/iam-hbp-*.tgz"
            }
        }
        stage("Build Images") {
            when {
                expression { !isPR }
            }
            parallel {
                stage("IAM") {
                    steps {
                        unstash name: "service"
                        sh "mv modules/service/target/universal/iam-service-*.tgz ./iam-service.tgz"
                        sh "oc start-build iam-build --from-file=iam-service.tgz --follow"
                    }
                }
                stage("IAM BBP") {
                    steps {
                        unstash name: "oidc-bbp"
                        sh "mv modules/oidc/bbp/target/universal/iam-bbp-*.tgz ./iam-bbp.tgz"
                        sh "oc start-build iam-bbp-build --from-file=iam-bbp.tgz --follow"
                    }
                }
                stage("IAM HBP") {
                    steps {
                        unstash name: "oidc-hbp"
                        sh "mv modules/oidc/hbp/target/universal/iam-hbp-*.tgz ./iam-hbp.tgz"
                        sh "oc start-build iam-hbp-build --from-file=iam-hbp.tgz --follow"
                    }
                }
            }
        }
        stage("Redeploy & Test") {
            when {
                expression { !isPR && !isRelease }
            }
            steps {
                sh "oc scale statefulset iam-bbp --replicas=0 --namespace=bbp-nexus-dev"
                sh "oc scale statefulset iam --replicas=0 --namespace=bbp-nexus-dev"
                sleep 10
                wait(ENDPOINT, false)
                sh "oc scale statefulset iam-bbp --replicas=1 --namespace=bbp-nexus-dev"
                sh "oc scale statefulset iam --replicas=1 --namespace=bbp-nexus-dev"
                sleep 120 // service readiness delay is set to 2 minutes
                openshiftVerifyService namespace: 'bbp-nexus-dev', svcName: 'iam-bbp', verbose: 'false'
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
                openshiftTag srcStream: 'iam-bbp', srcTag: 'latest', destStream: 'iam-bbp', destTag: version.substring(1), verbose: 'false'
                openshiftTag srcStream: 'iam-hbp', srcTag: 'latest', destStream: 'iam-hbp', destTag: version.substring(1), verbose: 'false'
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