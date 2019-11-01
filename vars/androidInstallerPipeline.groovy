def call(body) { // evaluate the body block, and collect configuration into the object
    def pipelineParams = [:]
    body.resolveStrategy = Closure.DELEGATE_FIRST
    body.delegate = pipelineParams

    body()
    def projectDir = pipelineParams['projectRoot'] ?: '.'
    def target = pipelineParams['target'] ?: 'release'
    def artifactoryURL = pipelineParams['artifactoryURL'] ?: 'http://staging-artifactory-internal.outcomehealthtech.com:8081/artifactory'
    def defaultTask = pipelineParams['defaultTask'] ?: 'clean assembleRelease'
    def upstreamProjects = pipelineParams['upstream'] ?: ''
    def publishTask = pipelineParams['publishTask'] ?: 'publishRelease'
    def artifactoryRepo = pipelineParams['artifactoryRepo'] ?: 'gradle-dev-local'
    def buildTask = pipelineParams['buildTask'] ?: defaultTask
    def publish = pipelineParams['publish'] ?: false
    pipeline {
        // our complete declarative pipeline can go in here

        agent {
            label 'android-agent'
        }
        parameters {
            booleanParam(defaultValue: publish,
                    description: 'Should this build be published to artifactory', name: 'publish' )
            booleanParam(defaultValue: mavenLocal,
                    description: 'Should this build be published to mavenLocal', name: 'mavenLocal' )
        }
        environment {
            ANDROID_HOME = '/home/jenkins/android'
        }


        triggers {
            upstream upstreamProjects
        }

        stages {
            stage('Init') {
                steps {
                    configFileProvider([configFile(fileId: 'gradle_properties_user',
                            targetLocation: '/home/jenkins/.gradle/gradle.properties')]) {
                        withCredentials([usernamePassword(credentialsId: '0dc99481-a399-4de3-9550-5b65b7f6ba37',
                                passwordVariable: 'GIT_PASSWORD',
                                usernameVariable: 'GIT_USERNAME')]) {
                            sh '''yes | ($ANDROID_HOME/tools/bin/sdkmanager --licenses )
                                  git config --global user.name devops-robot-contextmediainc
                                  git config --global user.email devops-robot@contextmediainc.com'''
                        }
                    }
                }
            }

            stage('Build') {
                steps {
                    dir(projectDir) {
                        script {
                            //current tag
                            if (publish) {
                                sh "./gradlew -Pnightly=CI tagNightly" 
                            }

                            sh "./gradlew ${buildTask}"
                            if (publish) {
                                sh "./gradlew -PartifactoryURL=${artifactoryURL} ${publishTask}"
                            }
                        }
                    }
                }
            }

        }

        post {
            success {
                archiveArtifacts artifacts: "**/build/outputs/apk/${target}/*.apk, **/build/version.properties, **/build/outputs/aar/*.aar, **/build/reports/lint-results.html", fingerprint: true, allowEmptyArchive: true, onlyIfSuccessful: true
            }
        }
    }
}

