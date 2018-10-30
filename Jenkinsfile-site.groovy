/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

pipeline {
    agent none
    tools { 
        maven 'Maven 3.3.9' 
        jdk 'jdk8' 
    }
    //label 'git-websites'
    stages {
        stage('SCM operation'){
            agent {label 'git-websites'}
            steps {
                checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CloneOption', noTags: true, reference: '', shallow: true], [$class: 'MessageExclusion', excludedMessage: 'Automated site publishing.*'], [$class: 'RelativeTargetDirectory', relativeTargetDir: 'master-branch']], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/apache/incubator-netbeans-mavenutils/']]])

                sh 'rm -rf asf-site-branch'
                //sh 'mkdir asf-site-branch'
                checkout([$class: 'GitSCM', branches: [[name: '*/asf-site']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'MessageExclusion', excludedMessage: 'Automated site publishing.*'], [$class: 'RelativeTargetDirectory', relativeTargetDir: 'asf-site-branch']], submoduleCfg: [], userRemoteConfigs: [[credentialsId: '9b041bd0-aea9-4498-a576-9eeb771411dd', url: 'https://gitbox.apache.org/repos/asf//incubator-netbeans-mavenutils/']]])
            }
        }
        stage('Build Site'){ 
            agent {label 'git-websites'}
            steps {
                
                script {
                    def mvnfoldersforsite  = ['parent','nbm-shared','nb-repository-plugin',/*'nbm-maven-harness',*/ 'nbm-maven-plugin']
                    def BASEDIR = pwd()
                    for (String mvnproject in mvnfoldersforsite) {
                        dir('master-branch/'+mvnproject) {
                            sh "mvn clean install site -Dmaven.repo.local=${BASEDIR}/.repository"
                            sh "mv target/site ${BASEDIR}/asf-site-branch/${mvnproject}/"
                        }
                    }
                }
                
                
            }
        }
        stage('Publish Site'){ 
            agent {label 'git-websites'}
            steps {
                dir('asf-site-branch') {
                    echo 'Adding content...'
                    sshagent (credentials: ['9b041bd0-aea9-4498-a576-9eeb771411dd']) {
                        sh 'git add -v .'
                        sh 'git commit -v -m "Automated site publishing by Jenkins build ${BUILD_NUMBER}'
                        sh 'git push -v origin asf-site'
                    }                 
                }
            }
        }
    }
}