pipeline {
    agent any

    /*
     * ---------------------------------------------------------------
     * EDIT THESE VALUES BEFORE PUSHING
     * ---------------------------------------------------------------
     */
    environment {
        DOCKERHUB_USER     = 'YOUR_DOCKERHUB_USERNAME'
        IMAGE_NAME         = 'task-manager'
        IMAGE_TAG          = "${env.BUILD_NUMBER}"
        APP_PORT           = '8081'
        APP_URL            = "http://localhost:8081"

        // Public HTTPS URL of the *separate* test repository
        TEST_REPO_URL      = 'https://github.com/YOUR_GITHUB_USERNAME/taskmanager-selenium-tests.git'
        TEST_REPO_BRANCH   = 'main'

        // Docker image with Chrome + Maven + JDK + ChromeDriver
        TEST_IMAGE         = 'markhobson/maven-chrome:latest'

        // Sender address configured in Jenkins SMTP (see SETUP_GUIDE.md)
        SMTP_FROM          = '[email protected]'
    }

    triggers {
        githubPush()
    }

    options {
        timestamps()
        buildDiscarder(logRotator(numToKeepStr: '20'))
        timeout(time: 30, unit: 'MINUTES')
    }

    stages {

        stage('Checkout App') {
            steps {
                checkout scm
            }
        }

        stage('Build Image') {
            steps {
                sh 'docker compose -f docker-compose.jenkins.yml build'
            }
        }

        stage('Deploy (Bring Up)') {
            steps {
                // Make sure any previous run is torn down before starting fresh
                sh 'docker compose -f docker-compose.jenkins.yml down -v || true'
                sh 'docker compose -f docker-compose.jenkins.yml up -d'
                // Give MySQL + the app a moment to settle
                sh 'sleep 20'
            }
        }

        stage('Smoke Test') {
            steps {
                sh "curl -fsS ${APP_URL}/health"
            }
        }

        stage('Checkout Selenium Tests') {
            steps {
                dir('selenium-tests') {
                    git branch: "${TEST_REPO_BRANCH}", url: "${TEST_REPO_URL}"
                }
            }
        }

        stage('Run Selenium Tests') {
            steps {
                sh """
                    docker run --rm \\
                        --network host \\
                        -v \"\$(pwd)/selenium-tests\":/work \\
                        -w /work \\
                        -e APP_URL=${APP_URL} \\
                        ${TEST_IMAGE} \\
                        mvn -B -Dapp.url=${APP_URL} test
                """
            }
            post {
                always {
                    junit testResults: 'selenium-tests/target/surefire-reports/*.xml',
                          allowEmptyResults: true
                    archiveArtifacts artifacts: 'selenium-tests/target/surefire-reports/*.xml',
                                     allowEmptyArchive: true
                }
            }
        }
    }

    post {
        always {
            script {
                /*
                 * Identify the Git author who triggered the run by reading
                 * the email from the latest commit on HEAD. This is what
                 * Jenkins receives from the GitHub webhook payload.
                 */
                def commitEmail = sh(
                    script: "git log -1 --pretty=format:'%ae'",
                    returnStdout: true
                ).trim()

                if (!commitEmail) {
                    commitEmail = env.SMTP_FROM
                    echo "WARNING: could not detect commit author email, falling back to SMTP_FROM"
                }

                def status = currentBuild.currentResult
                def colour = (status == 'SUCCESS') ? 'green' : 'red'

                emailext(
                    to: commitEmail,
                    from: env.SMTP_FROM,
                    subject: "[Task Manager CI] Build #${env.BUILD_NUMBER} - ${status}",
                    mimeType: 'text/html',
                    body: """
                        <p>Hi,</p>
                        <p>Your push to <b>${env.JOB_NAME}</b> triggered a Jenkins pipeline run.</p>
                        <ul>
                          <li><b>Build:</b> #${env.BUILD_NUMBER}</li>
                          <li><b>Status:</b> <span style="color:${colour}"><b>${status}</b></span></li>
                          <li><b>Triggered by:</b> ${commitEmail}</li>
                          <li><b>Console:</b> <a href="${env.BUILD_URL}console">${env.BUILD_URL}console</a></li>
                          <li><b>Test Report:</b> <a href="${env.BUILD_URL}testReport">${env.BUILD_URL}testReport</a></li>
                        </ul>
                        <p>JUnit XML reports from the Selenium suite are attached.</p>
                        <hr/>
                        <p style="color:#888;font-size:12px">Sent automatically by Jenkins running on AWS EC2.</p>
                    """,
                    attachmentsPattern: 'selenium-tests/target/surefire-reports/*.xml',
                    attachLog: false
                )
            }
        }
    }
}
