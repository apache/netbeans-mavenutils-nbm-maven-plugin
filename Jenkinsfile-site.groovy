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
        jdk 'JDK 1.8 (latest)' 
    }
    stages {
        stage('SCM operation'){
            agent {label 'ubuntu'}
            steps {
                checkout([$class: 'GitSCM', branches: [[name: '*/master']], doGenerateSubmoduleConfigurations: false, extensions: [[$class: 'CloneOption', noTags: true, reference: '', shallow: true], [$class: 'MessageExclusion', excludedMessage: 'Automated site publishing.*'], [$class: 'RelativeTargetDirectory', relativeTargetDir: 'master-branch']], submoduleCfg: [], userRemoteConfigs: [[url: 'https://github.com/apache/incubator-netbeans-mavenutils/']]])

                sh 'rm -rf stagedsite'
		sh 'mkdir stagedsite'
            }
        }
        stage('Build Site'){ 
            agent {label 'ubuntu'}
            steps {
                // build site skin
                script {
                    def mvnfoldersforsite  = ['parent','webskin']
                    def BASEDIR = pwd()
                    for (String mvnproject in mvnfoldersforsite) {
                        dir('master-branch/'+mvnproject) {
                            sh "mvn clean install -Dmaven.repo.local=${BASEDIR}/.repository"
                        }
                    }
                }
                // build site
                script {
                    def mvnfoldersforsite  = ['parent','nbm-shared','nb-repository-plugin',/*'nbm-maven-harness',*/ 'nbm-maven-plugin']
                    def BASEDIR = pwd()
                    for (String mvnproject in mvnfoldersforsite) {
                        dir('master-branch/'+mvnproject) {
                            sh "mvn clean install site -Dmaven.repo.local=${BASEDIR}/.repository"
                            sh "mv target/site ${BASEDIR}/stagedsite/${mvnproject}/"
                        }
                    }
                }
                zip zipFile:'mavenusite.zip',archive:false,dir:'stagedsite'
                archiveArtifacts artifacts:'mavenusite.zip'
  
                
            }
        }               
    }
}
