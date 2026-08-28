pipeline {
    agent {
        node {
            label ''
            customWorkspace '/data2/deploy/intelligent-test-agent/workspace'
        }
    }

    options {
        skipDefaultCheckout(true)
        disableConcurrentBuilds()
        timeout(time: 150, unit: 'MINUTES')
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '30'))
    }

    parameters {
        choice(name: 'ACTION', choices: ['DEPLOY', 'ROLLBACK'], description: '部署 release 分支，或回滚到历史不可变标签。')
        string(name: 'ROLLBACK_TAG', defaultValue: '', trim: true, description: 'ACTION=ROLLBACK 时必填，例如 release-3-deadbeef。')
    }

    environment {
        RELEASE_ROOT = '/data2/deploy/intelligent-test-agent/releases'
        LOG_ROOT = '/data2/deploy/intelligent-test-agent/logs'
        SHARED_ROOT = '/data2/deploy/intelligent-test-agent/shared'
        ENV_FILE = '/data2/deploy/intelligent-test-agent/shared/runtime.env'
        RUNTIME_DATA_ROOT = '/home/abc/intelligent-test-agent-dev/.testagent'
        MAVEN_CACHE_DIR = '/data2/deploy/shared/maven-repository'
        PNPM_STORE_DIR = '/data2/deploy/shared/pnpm-store'
        COREPACK_CACHE_DIR = '/data2/deploy/shared/corepack-cache'
        BACKEND_BASE_URL = 'http://192.168.8.100:18082'
        FRONTEND_URL = 'http://192.168.8.100:3000'
        RELEASE_SCRIPT = 'deploy/local/jenkins-release.sh'
    }

    stages {
        stage('Checkout release') {
            steps {
                deleteDir()
                checkout([
                    $class: 'GitSCM',
                    branches: [[name: '*/release']],
                    userRemoteConfigs: [[
                        credentialsId: 'intelligent-test-agent-git-ssh',
                        url: 'ssh://git@192.168.8.100:8022/wrui/intelligent-test-agent.git'
                    ]]
                ])
                script {
                    env.GIT_COMMIT_FULL = sh(script: 'git rev-parse HEAD', returnStdout: true).trim()
                    if (params.ACTION == 'DEPLOY') {
                        env.RELEASE_TAG = "release-${env.BUILD_NUMBER}-${env.GIT_COMMIT_FULL.take(8)}"
                    } else {
                        env.RELEASE_TAG = params.ROLLBACK_TAG.trim()
                        sh '"$RELEASE_SCRIPT" validate-tag "$RELEASE_TAG"'
                    }
                    env.RELEASE_DIR = "${env.RELEASE_ROOT}/${env.RELEASE_TAG}"
                    env.RUN_LOG_DIR = "${env.LOG_ROOT}/${env.BUILD_NUMBER}-${params.ACTION.toLowerCase()}-${env.RELEASE_TAG}"
                    currentBuild.displayName = "#${env.BUILD_NUMBER} ${params.ACTION} ${env.RELEASE_TAG}"
                }
            }
        }

        stage('Release contract') {
            steps {
                sh 'tools/verify-jenkins-release.sh'
                sh '"$RELEASE_SCRIPT" validate-host'
            }
        }

        stage('Build backend and frontend') {
            when {
                expression { params.ACTION == 'DEPLOY' }
            }
            steps {
                sh '"$RELEASE_SCRIPT" build'
            }
        }

        stage('Prepare immutable release') {
            when {
                expression { params.ACTION == 'DEPLOY' }
            }
            steps {
                sh '"$RELEASE_SCRIPT" prepare "$RELEASE_TAG" "$GIT_COMMIT_FULL" "$RELEASE_DIR"'
            }
        }

        stage('Verify cloned database upgrade') {
            when {
                expression { params.ACTION == 'DEPLOY' }
            }
            steps {
                sh '"$RELEASE_SCRIPT" verify-database-upgrade "$RELEASE_DIR" "$RELEASE_TAG"'
            }
        }

        stage('Publish and verify') {
            steps {
                sh '''
                    "$RELEASE_SCRIPT" deploy "$RELEASE_DIR" "$RELEASE_TAG"
                    "$RELEASE_SCRIPT" verify-deployment "$RELEASE_TAG"
                '''
            }
        }
    }

    post {
        always {
            sh '"$RELEASE_SCRIPT" collect-logs "$RUN_LOG_DIR" || true'
        }
        success {
            sh '"$RELEASE_SCRIPT" record-result "$RELEASE_DIR" SUCCESS'
        }
        failure {
            sh '''
                if [ -n "${RELEASE_DIR:-}" ] && [ -d "$RELEASE_DIR" ]; then
                    "$RELEASE_SCRIPT" record-result "$RELEASE_DIR" FAILED || true
                fi
            '''
        }
    }
}
