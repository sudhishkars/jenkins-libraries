def call(Map pipelineParams) {
  pipeline {
    agent any

    tools {
      maven "maven-390"
    }

    environment {
      //Anypoint Platform connected app credentials (clientId/secret) - appName: ci-cd-connected-app
      ANYPOINT_CONNECTED_APP_CREDENTIALS = credentials("anypoint-deploy")

      //Maven settings XML
      MAVEN_SETTINGS_XML=credentials('mvn-settings')

      // PORTFOLIO_NAME=${pipelineParams.portfolio}
      // PORTFOLIO_NAME_LOWER="${PORTFOLIO_NAME.toLowerCase()}"   

      CONNECTED_APP_CLIENT_ID = ${ANYPOINT_CONNECTED_APP_CREDENTIALS.USER}
      CONNECTED_APP_CLIENT_SECRET = ${ANYPOINT_CONNECTED_APP_CREDENTIALS.PSW}           

      // MULE_VERSION = mwDefaults.deployment_Params_Defaults.muleVersion
      // RTF_PROVIDER = mwDefaults.deployment_Params_Defaults.provider
      // SKIP_DEPLOY_VERIFY = mwDefaults.deployment_Params_Defaults.skipDeployVerification



      // ENFORCE_REPLICAS_ACROSS_NODES = mwDefaults.deployment_Params_Defaults.enforceReplicasAcrossNodes
      // UPDATESTRATEGY = mwDefaults.deployment_Params_Defaults.updateStrategy
      // CLUSTERED = mwDefaults.deployment_Params_Defaults.clustered
      // FORWARD_SSL_SESSION = mwDefaults.deployment_Params_Defaults.forwardSSLSession
      // LAST_MILE_SECURITY = mwDefaults.deployment_Params_Defaults.lastMileSecurity
      // PERSISTENT_OBJECT_STORE = mwDefaults.deployment_Params_Defaults.persistentObjectStore

      // ANYPOINT_URL = mwDefauls.deployment_Params_Defaults.anypoint_url

      // ANYPOINT_URL = "https://anypoint.mulesoft.com"
      // MULE_VERSION =  "4.4.0"
      // RTF_PROVIDER = "MC"
      // SKIP_DEPLOY_VERIFY="false"

      // ENFORCE_REPLICAS_ACROSS_NODES = "true"
      // UPDATESTRATEGY = "rolling"
      // CLUSTERED = "false"
      // FORWARD_SSL_SESSION = "false"
      // LAST_MILE_SECURITY = "false"
      // PERSISTENT_OBJECT_STORE = "false"

      // RUN_TESTS = pipelineParams.runTests
      // IS_PRODUCTION = "false"
    }

    //def appName = "${PORTFOLIO_NAME_LOWER}-${pipelineParams.projectName}-${ANYPOINT_DEV}"

    stages {

      stage('Compile') {
        echo "Branch -- ${env.GIT_BRANCH}"
        steps {
          sh "mvn -s ${MAVEN_SETTINGS_XML} -B -U clean compile"
        }    
      }

      stage('Test') {
        when {
          expression { "${RUN_TESTS}" == 'true' }
        }
        steps {
          sh "mvn -s ${MAVEN_SETTINGS_XML} -B -U test"
        }
      }

      stage('Publish to Exchange') {
        steps {
          script {
             sh "mvn -s ${MAVEN_SETTINGS_XML} -B -U -PExchange deploy -DskipTests"
          }
        }
      }

      // stage('Deploy to RTF Dev1') {
      //   when {
      //     allOf { 
      //       expression { "${env.GIT_BRANCH}" == 'develop' }
      //       expression { "${IS_PRODUCTION}" == 'false' }
      //     }
      //   }
      //   environment {
      //     MULE_ENV="dev1"
      //     ANYPOINT_ENV=${MULE_ENV}

      //     PORTFOLIO_CREDENTIALS = credentials("${PORTFOLIO_NAME_LOWER}-anypoint-${MULE_ENV}")

      //     SECRET_KEY=credentials("${PORTFOLIO_NAME_LOWER}-${MULE_ENV}-key")          
      //     ANYPOINT_ENV_CLIENT_ID = ${PORTFOLIO_CREDENTIALS.USER}
      //     ANYPOINT_ENV_CLIENT_SECRET = ${PORTFOLIO_CREDENTIALS.PSW} 

      //     RTF_CLUSTER_NAME = mwDefauls.portFolio_Env_Mappings["${PORTFOLIO_NAME}"]["${MULE_ENV}"]

      //     CPU_RESERVED = mwDefaults.DEV1_Resource_Defaults.cpu_reserved
      //     CPU_LIMIT = mwDefaults.DEV1_Resource_Defaults.cpu_limit
      //     MEMORY_RESERVED = mwDefaults.DEV1_Resource_Defaults.memory_reserved
      //     REPLICAS = mwDefaults.DEV1_Resource_Defaults.replicas

      //     SECRET_ENV_KEY = ${SECRET_KEY}                    
      //   }

      //   steps {
      //     script { 
      //       print "App Name: " + appName         
      //       sh "mvn -s ${MAVEN_SETTINGS_XML} -B -U mule:deploy -Dmule.artifact=myArtifact.jar -Dmule.app.name=${appName}"
      //     }
      //   }
      // }

    }
  }
}
