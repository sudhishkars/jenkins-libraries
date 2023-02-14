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
      MAVEN_SETTINGS_XML = credentials('mvn-settings')

      PORTFOLIO_NAME=${pipelineParams.portfolio}
      PORTFOLIO_NAME_LOWER="${PORTFOLIO_NAME.toLowerCase()}"

      MULE_ENV="dev1"
      ANYPOINT_ENV=${MULE_ENV}

      PORTFOLIO_DEV_CREDENTIALS = credentials("${PORTFOLIO_NAME_LOWER}-anypoint-${MULE_ENV}")

      SECRET_DEV_KEY=credentials("${PORTFOLIO_NAME_LOWER}-${MULE_ENV}-key")

      RTF_CLUSTER_NAME = mwDefauls.portFolio_Env_Mappings[(PORTFOLIO_NAME)][(MULE_ENV)]

      MULE_VERSION = mwDefaults.deployment_Params_Defaults.muleVersion
      RTF_PROVIDER = mwDefaults.deployment_Params_Defaults.provider
      RTF_SKIP_DEPLOY_VERIFY = mwDefaults.deployment_Params_Defaults.skipDeployVerification

      CONNECTED_APP_CLIENT_ID = ${ANYPOINT_CONNECTED_APP_CREDENTIALS.USER}
      CONNECTED_APP_CLIENT_SECRET = ${ANYPOINT_CONNECTED_APP_CREDENTIALS.PSW}

      ENFORCE_REPLICAS_ACROSS_NODES = mwDefaults.deployment_Params_Defaults.enforceReplicasAcrossNodes
      UPDATESTRATEGY = mwDefaults.deployment_Params_Defaults.updateStrategy
      CLUSTERED = mwDefaults.deployment_Params_Defaults.clustered
      FORWARD_SSL_SESSION = mwDefaults.deployment_Params_Defaults.forwardSSLSession
      LAST_MILE_SECURITY = mwDefaults.deployment_Params_Defaults.lastMileSecurity
      PERSISTENT_OBJECT_STORE = mwDefaults.deployment_Params_Defaults.persistentObjectStore

      CPU_RESERVED = mwDefaults.DEV1_Resource_Defaults.cpu_reserved
      CPU_LIMIT = mwDefaults.DEV1_Resource_Defaults.cpu_limit
      MEMORY_RESERVED = mwDefaults.DEV1_Resource_Defaults.memory_reserved
      REPLICAS = mwDefaults.DEV1_Resource_Defaults.replicas

      ANYPOINT_ENV_CLIENT_ID = ${PORTFOLIO_DEV_CREDENTIALS.USER}
      ANYPOINT_ENV_CLIENT_SECRET = ${PORTFOLIO_DEV_CREDENTIALS.PSW}

      ANYPOINT_URL = mwDefauls.deployment_Params_Defaults.anypoint_url

      SECRET_ENV_KEY = {SECRET_DEV_KEY}

      RUN_TESTS = pipelineParams.runTests
      IS_PRODUCTION = "false"
    }

    def appName = "${pipelineParams.projectName}-${ANYPOINT_DEV}"

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

      stage('Deploy to RTF Dev1') {
        when {
          allOf { 
            expression { "${env.GIT_BRANCH}" == 'develop' }
            expression { "${IS_PRODUCTION}" == 'false' }
          }
        }

        steps {
          script {            
            sh "mvn -s ${MAVEN_SETTINGS_XML} -B -U mule:deploy -Dmule.artifact=myArtifact.jar -Dmule.app.name=appName"
          }
        }
      }

    }
  }
}
