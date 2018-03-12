def version = env.BRANCH_NAME

pipeline {
    agent none

    stages {
        stage("Review") {
            when {
                expression { env.CHANGE_ID != null }
            }
            parallel {
                stage("StaticAnalysis") {
                    steps {
                        node("slave-sbt") {
                            checkout scm
                            sh 'sbt clean scalafmtCheck scalafmtSbtCheck scapegoat'
                        }
                    }
                }
                stage("Tests/Coverage") {
                    steps {
                        node("slave-sbt") {
                            checkout scm
                            sh 'sbt clean coverage test coverageReport coverageAggregate'
                        }
                        node("slave-sbt") {
                            checkout scm
                            sh 'sbt universal:packageZipTarball'
                            stash name: "tgz", includes: "modules/service/target/universal/iam-service-*.tgz"
                        }
                        node("slave-sbt") {
                            unstash name: "tgz"
                            sh "ls -laR"
                            sh "mv modules/service/target/universal/iam-service-*.tgz ./iam-service.tgz"
                            sh "oc start-build iam-build --from-file=iam-service.tgz --follow"
                            openshiftTag srcStream: 'iam', srcTag: 'latest', destStream: 'iam', destTag: version.substring(1), verbose: 'true'
                        }
                    }
                }
            }
        }
        stage("Release") {
            when {
                expression { env.CHANGE_ID == null }
            }
            steps {
                node("slave-sbt") {
                    checkout scm
                    sh 'sbt releaseEarly'
                }
            }
        }
        stage("Build Image") {
            when {
                expression { version ==~ /v\d+\.\d+\.\d+.*/ }
            }
            steps {
                node("slave-sbt") {
                    checkout scm
                    sh 'sbt universal:packageZipTarball'
                    stash name: "tgz", includes: "modules/service/target/universal/iam-service-*.tgz"
                }
                node("slave-sbt") {
                    unstash name: "tgz"
                    sh "ls -laR"
                    sh "mv modules/service/target/universal/iam-service-*.tgz ./iam-service.tgz"
                    sh "oc start-build iam-build --from-file=iam-service.tgz --follow"
                    openshiftTag srcStream: 'iam', srcTag: 'latest', destStream: 'iam', destTag: version.substring(1), verbose: 'true'
                }
            }
        }
    }
}
