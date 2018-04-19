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
                    sh 'sbt releaseEarly universal:packageZipTarball'
                    stash name: "service", includes: "modules/service/target/universal/iam-service-*.tgz"
                    stash name: "oidc-bbp", includes: "modules/oidc/bbp/target/universal/iam-bbp-*.tgz"
                    stash name: "oidc-hbp", includes: "modules/oidc/hbp/target/universal/iam-hbp-*.tgz"
                }
            }
        }
        stage("Build Image") {
            when {
                expression { version ==~ /v\d+\.\d+\.\d+.*/ }
            }
            parallel {
                stage("IAM") {
                    steps {
                        node("slave-sbt") {
                            unstash name: "service"
                            sh "mv modules/service/target/universal/iam-service-*.tgz ./iam-service.tgz"
                            sh "oc start-build iam-v0-build --from-file=iam-service.tgz --follow"
                            openshiftTag srcStream: 'iam-v0', srcTag: 'latest', destStream: 'iam-v0', destTag: version.substring(1), verbose: 'false'
                        }
                    }
                }
                stage("IAM BBP") {
                    steps {
                        node("slave-sbt") {
                            unstash name: "oidc-bbp"
                            sh "mv modules/oidc/bbp/target/universal/iam-bbp-*.tgz ./iam-bbp.tgz"
                            sh "oc start-build iam-bbp-v0-build --from-file=iam-bbp.tgz --follow"
                            openshiftTag srcStream: 'iam-bbp-v0', srcTag: 'latest', destStream: 'iam-bbp-v0', destTag: version.substring(1), verbose: 'false'
                        }
                    }
                }
                stage("IAM HBP") {
                    steps {
                        node("slave-sbt") {
                            unstash name: "oidc-hbp"
                            sh "mv modules/oidc/hbp/target/universal/iam-hbp-*.tgz ./iam-hbp.tgz"
                            sh "oc start-build iam-hbp-v0-build --from-file=iam-hbp.tgz --follow"
                            openshiftTag srcStream: 'iam-hbp-v0', srcTag: 'latest', destStream: 'iam-hbp-v0', destTag: version.substring(1), verbose: 'false'
                        }
                    }
                }
            }
        }
    }
}
