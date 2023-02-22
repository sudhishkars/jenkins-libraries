def call(Map pipelineParams) {

  def deployProfile = "rtf"     

  pipeline {
    agent any

    tools {
      maven "mvn-385"
    }

    environment {
      PORTFOLIO_NAME="${pipelineParams.portfolio}"
      PORTFOLIO_NAME_LOWER="${PORTFOLIO_NAME.toLowerCase()}" 
      PROJECT="${pipelineParams.projectName}"    

      RUN_TESTS = "${pipelineParams.runTests}"
      IS_PRODUCTION = "false"

      MVN_ARGS = "${mwDefaults.mvnArgs}"
    }

    stages {

      stage('init') {
        steps {
          script {

            def deployParams1 = mwDefaults.deployment_Params_Defaults

            deployUtils.getDeploymentConfigs(deployParams1,"${PORTFOLIO_NAME_LOWER}","dev1","${PROJECT}")  

            deployProfile =  deployParams1.deploy_profile        
          }
        }
      }


      // stage('Pacakge') {        
      //   steps {
      //     echo "Branch -- ${env.GIT_BRANCH}"
      //     //sh "mvn ${mwDefaults.mvnArgs} -U clean compile"
      //     sh 'mvn $MVN_ARGS -U  clean package' 
      //   }    
      // }

      // stage('Test') {
      //   when {
      //     expression { "${RUN_TESTS}" == 'true' }
      //   }
      //   steps {
      //     //sh "mvn ${mwDefaults.mvnArgs} -U test"
      //     sh 'mvn $MVN_ARGS -U  test' 
      //   }
      // }

      stage('Publish to Artifact Repository') {
        when {
          anyOf {
            branch 'develop'
            branch 'master'
            branch 'release/*'
          }
        }        
        steps {
          script {
            println 'Deploy Profile: ' + deployProfile
            withEnv (["DEPLOY_PROFILE=${deployProfile}"]) {
              sh 'mvn $MVN_ARGS -U  -P$DEPLOY_PROFILE deploy -DskipTests'
            }                    
            //sh "mvn ${mwDefaults.mvnArgs} -U -P$deployProfile deploy -DskipTests"
          }
        }
      }

      stage('Deploy to RTF Dev1') {
        when {
          allOf { 
            branch 'develop'
            //expression { "${env.GIT_BRANCH}" == 'develop' }
            expression { "${IS_PRODUCTION}" == 'false' }
          }
        }
        steps {
          script {
            //def appName = "${PORTFOLIO_NAME_LOWER}-${pipelineParams.projectName}-${ANYPOINT_ENV}"
            //println "App Name: ${APP_NAME}"

            def muleEnv = "dev1"
            def anypointEnv = muleEnv

            def deployParams = mwDefaults.deployment_Params_Defaults

            deployUtils.getDeploymentConfigs(deployParams,"${PORTFOLIO_NAME_LOWER}",anypointEnv,"${PROJECT}")

            def clusters = deployParams['clusters']

            deployProfile = deployParams.deploy_profile

            withEnv ([
                "MULE_ENV=${muleEnv}",
                "ANYPOINT_ENV=${anypointEnv}",
                "APP_NAME= ${PORTFOLIO_NAME_LOWER}-${PROJECT}-${anypointEnv}",
                "CPU_RESERVED=${deployParams.cpu_reserved}",
                "CPU_LIMIT=${deployParams.cpu_limit}",
                "MEMORY_RESERVED=${deployParams.memory_reserved}",
                "REPLICAS=${deployParams.replicas}",
                "MULE_VERSION=${deployParams.mule_version}",
                "RTF_PROVIDER=${deployParams.provider}",
                "SKIP_DEPLOY_VERIFY=${deployParams.skip_deploy_verify}",
                "ENFORCE_REPLICAS_ACROSS_NODES=${deployParams.enforce_replicas_across_nodes}",
                "UPDATE_STRATEGY=${deployParams.update_strategy}",
                "CLUSTERED=${deployParams.clustered}",
                "FORWARD_SSL_SESSION=${deployParams.forward_ssl_session}",
                "LAST_MILE_SECURITY=${deployParams.last_mile_security}",
                "PERSISTENT_OBJECT_STORE=${deployParams.persistent_object_store}",
                "DEPLOY_PROFILE=${deployProfile}"
            ]) {
                  withCredentials([
                    usernamePassword(credentialsId: "${PORTFOLIO_NAME_LOWER}-${MULE_ENV}-creds", usernameVariable: 'ap_user', passwordVariable: 'ap_pass'),
                    string(credentialsId: "${PORTFOLIO_NAME_LOWER}-${MULE_ENV}-key", variable: 'key')
                    ]) { 
                      println "Mule App: ${APP_NAME}, MULE Env: ${MULE_ENV}, UPDATE_STRATEGY: ${UPDATE_STRATEGY}"
                      println "REPLICAS: ${REPLICAS}, CPU_RESERVED Env: ${CPU_RESERVED}, CPU_LIMIT: ${CPU_LIMIT}"
                      for (cluster in clusters)   { 
                        println "Deploying to Cluster: " + cluster
                        sh 'mvn $MVN_ARGS -P$DEPLOY_PROFILE mule:deploy -Dmule.artifact=dummy.jar -Danypoint.env.clientId=$ap_user -Danypoint.env.clientSecret=$ap_pass -Dsecret.key=$key -Drtf.cluster=$cluster'
                      }
                  }                     
                }
        }
      }

    }
  }
}
}