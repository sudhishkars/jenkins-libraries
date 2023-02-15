def call(Map pipelineParams) {
  pipeline {
    agent any

    tools {
      maven "maven-390"
    }

    environment {

      //Maven settings XML
      // MAVEN_SETTINGS_XML=credentials('mvn-settings')

      PORTFOLIO_NAME="${pipelineParams.portfolio}"
      PORTFOLIO_NAME_LOWER="${PORTFOLIO_NAME.toLowerCase()}"       

      MULE_VERSION = "${mwDefaults.deployment_Params_Defaults.muleVersion}"
      RTF_PROVIDER = "${mwDefaults.deployment_Params_Defaults.provider}"
      SKIP_DEPLOY_VERIFY = "${mwDefaults.deployment_Params_Defaults.skipDeployVerification}"

      ENFORCE_REPLICAS_ACROSS_NODES = "${mwDefaults.deployment_Params_Defaults.enforceReplicasAcrossNodes}"
      UPDATESTRATEGY = "${mwDefaults.deployment_Params_Defaults.updateStrategy}"
      CLUSTERED = "${mwDefaults.deployment_Params_Defaults.clustered}"
      FORWARD_SSL_SESSION = "${mwDefaults.deployment_Params_Defaults.forwardSSLSession}"
      LAST_MILE_SECURITY = "${mwDefaults.deployment_Params_Defaults.lastMileSecurity}"
      PERSISTENT_OBJECT_STORE = "${mwDefaults.deployment_Params_Defaults.persistentObjectStore}"

      //ANYPOINT_URL = "${mwDefauls.deployment_Params_Defaults.anypoint_url}"

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

    

    stages {

      stage('Compile') {        
        steps {
          echo "Branch -- ${env.GIT_BRANCH}"
          // withCredentials([file(credentialsId: "mvn-settings", variable: 'MAVEN_SETTINGS_XML')]) {
          //     print "SETTINGS FILE: " + $MAVEN_SETTINGS_XML 
          //     sh 'mvn -s $MAVEN_SETTINGS_XML -B -U clean compile'
          // }
          sh "mvn ${mvnDefaults.maven_args} -U clean compile"
        }    
      }

      stage('Test') {
        when {
          expression { "${RUN_TESTS}" == 'true' }
        }
        steps {
          // withCredentials([file(credentialsId: "mvn-settings", variable: 'MAVEN_SETTINGS_XML')]) {
          //   sh 'mvn -s $MAVEN_SETTINGS_XML -B -U test'
          // }
          sh "mvn ${mvnDefaults.maven_args} -U test"
        }
      }

      stage('Publish to Exchange') {
        steps {
          script {
            // withCredentials([file(credentialsId: "mvn-settings", variable: 'MAVEN_SETTINGS_XML')]) {
            //   sh 'mvn -s $MAVEN_SETTINGS_XML -B -U -PExchange deploy -DskipTests'
            // }
            sh "mvn ${mvnDefaults.maven_args} -U -PExchange deploy -DskipTests"
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
        environment {
          MULE_ENV="dev1"
          ANYPOINT_ENV="${MULE_ENV}"

          RTF_CLUSTER_NAME = "${mwDefauls.portFolio_Env_Mappings[${PORTFOLIO_NAME}][${MULE_ENV}]}"

          CPU_RESERVED = "${mwDefaults.DEV1_Resource_Defaults.cpu_reserved}"
          CPU_LIMIT = "${mwDefaults.DEV1_Resource_Defaults.cpu_limit}"
          MEMORY_RESERVED = "${mwDefaults.DEV1_Resource_Defaults.memory_reserved}"
          REPLICAS = "${mwDefaults.DEV1_Resource_Defaults.replicas}"                 
        }

        steps {
          script {
            def appName = "${PORTFOLIO_NAME_LOWER}-${pipelineParams.projectName}-${ANYPOINT_DEV}"
            print "App Name: " + appName
            print "RTF Cluster: " + ${RTF_CLUSTER_NAME}
            sh "mvn ${mvnDefaults.maven_args} mule:deploy -Dmule.artifact=dummy.jar -Dmule.app.name=${appName} -Danypoint.env.clientId=${ap_user} -Danypoint.env.clientSecret=${ap_pass} -Dsecret.key=${key}"
            // withCredentials(
            //   [file(credentialsId: "mvn-settings", variable: 'MAVEN_SETTINGS_XML')],
            //   [usernamePassword(credentialsId: "${PORTFOLIO_NAME_LOWER}-${MULE_ENV}-key", usernameVariable: 'AP_USER', passwordVariable: 'AP_PASS')],
            //   [string(credentialsId: "${PORTFOLIO_NAME_LOWER}-${MULE_ENV}-key", variable: 'key')]
            //   ) {
            //     sh 'mvn -s $MAVEN_SETTINGS_XML -B -U mule:deploy -Dmule.artifact=dummy.jar -Dmule.app.name=${appName} -Danypoint.env.clientId=${ap_user} -Danypoint.env.clientSecret=${ap_pass} -Dsecret.key=${key}'
            // }                     
          }
        }
      }

    }
  }
}
