//login to Agent-1 here wwe are using agent ec2 so login to that manually through super putty 
//do sudo visudo or sudo cd /etc/sudoers file 
// add jenkins ALL=(ALL) NOPASSWD: ALL   after %wheel 
// this is adding becasue it will not run as root it will run as jenkins user so adding like this 
//do aws configure command not in root user do it in normal user in agent-1 where ever agent u do all this
//then only pipeline will execute and one for one ec2/agent/jenins server


//Before share library file 
def call(Map configMap) {
pipeline {
    agent {
        node {
            label 'Agent-1'
        }
    }
    environment {
        packageVersion = ''
        nexusURL = 'nexus.devopswitharun.online:8081'   //we gave public ip by creating route53 record of nexus ec2 with port but in project we use static ip 
    }
    options {
        ansiColor('xterm')
        timeout(time:1, unit:'HOURS')
        disableConcurrentBuilds()
    }
    parameters {
        booleanParam(name: 'Deploy', defaultValue: false, description: 'If Deploy Want Do  This ?')
    }
    stages {
        stage('Get Version') {
            steps {
                script {
                    sh 'cd catalogue/'
                    def packageJSON = readJSON file: 'catalogue/package.json'
                    packageVersion = packageJSON.version
                    echo "Application version is : $packageVersion"
                }
            }
        }
        stage('Install Dependencies') {
            steps {
                sh """
                    sudo dnf module disable nodejs -y
                    sudo dnf module enable nodejs:18 -y
                    sudo dnf install nodejs -y
                    cd catalogue/
                    npm install  
                """
            }
        }
        stage('Unit Testing') {
            steps {
                sh """
                    echo "unit test runs here"
                """
            }
        }
        stage('Sonar Scan') {
            steps {
                sh """
                    cd catalogue/
                    sonar-scanner 
                """
            }
        }
        stage('Build package') {
            steps {
                sh """
                    sudo dnf install zip -y
                    cd catalogue/
                    ls -la       
                    pwd             
                    zip -q -r catalogue.zip ./* -x ".git" -x ".zip"
                    ls -ltr 
                """
            }
        }
        stage('Publish Artifact') {
            steps {
                sh """
                    pwd
                    cd catalogue/
                """
                nexusArtifactUploader(
                    nexusVersion: 'nexus3',
                    protocol: 'http',
                    nexusUrl: "${nexusURL}",
                    groupId: 'com.roboshop',
                    version: "${packageVersion}",
                    repository: 'catalogue',
                    credentialsId: 'nexus-login-credentials',
                    artifacts: [
                        [artifactId: 'catalogue',
                        classifier: '',
                        file: 'catalogue/catalogue' + '.zip',
                        type: 'zip']
                    ]
                )
            }
        }
        stage ('Deploy') {
            when {
                expression {
                    params.Deploy 
                }
            }
            steps {
                script {
                    def buildParams = [
                        string(name: 'version', value: "${packageVersion}"),
                        string(name: 'environment', value: "dev")
                    ]
                    build job: "RoboShop-CD/catalogue-CD-deploy", wait: true, parameters: buildParams 
                }
            }
        }
    }
    post {
        always {
            echo "Always runs whether pipeline success or failure"
            deleteDir()

        }
        success {
            echo "Pipeline is runned successfully "
        }
        failure {
            echo "Pipeline is Failed "
        }
    }
}
}