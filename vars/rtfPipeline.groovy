def call(Map pipelineParams) {
  pipeline {
    agent any

    tools {
      maven "mvn-385"
    }

    environment {

      //Maven settings XML
      // MAVEN_SETTINGS_XML=credentials('mvn-settings')

      PORTFOLIO_NAME="${pipelineParams.portfolio}"
      PORTFOLIO_NAME_LOWER="${PORTFOLIO_NAME.toLowerCase()}" 
      PROJECT="${pipelineParams.projectName}"    

      RUN_TESTS = "${pipelineParams.runTests}"
      IS_PRODUCTION = "false"

      MVN_ARGS = "${mwDefaults.mvnArgs}"
    }

    

    stages {


      // stage('Compile') {        
      //   steps {
      //     echo "Branch -- ${env.GIT_BRANCH}"
      //     sh "mvn ${mwDefaults.mvnArgs} -U clean compile"
      //   }    
      // }

      // stage('Test') {
      //   when {
      //     expression { "${RUN_TESTS}" == 'true' }
      //   }
      //   steps {
      //     sh "mvn ${mwDefaults.mvnArgs} -U test"
      //   }
      // }

      // stage('Publish to Exchange') {
      //   steps {
      //     script {
      //       // withCredentials([file(credentialsId: "mvn-settings", variable: 'MAVEN_SETTINGS_XML')]) {
      //       //   sh 'mvn -s $MAVEN_SETTINGS_XML -B -U -PExchange deploy -DskipTests'
      //       // }
      //       sh "mvn ${mwDefaults.mvnArgs} -U -Pexchange deploy -DskipTests"
      //     }
      //   }
      // }

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

          //PORTFOLIO_ENV = "${mwDefaults.portFolio_Env_Mappings[PORTFOLIO_NAME]}"

          // DEPLOY_PARAMS="${deployUtils.getDeploymentConfigs(mwDefaults.deployment_Params_Defaults, 
          //           mwDefaults.deployment_Params_Constants,PORTFOLIO_NAME_LOWER,ANYPOINT_DEV,PROJECT)}"
          
 
          // RTF_CLUSTER_NAME = "${mwDefaults.portFolio_Env_Mappings[PORTFOLIO_NAME][MULE_ENV][0]}"

          // CPU_RESERVED = "${mwDefaults.DEV1_Resource_Defaults.cpu_reserved}"
          // CPU_LIMIT = "${mwDefaults.DEV1_Resource_Defaults.cpu_limit}"
          // MEMORY_RESERVED = "${mwDefaults.DEV1_Resource_Defaults.memory_reserved}"
          // REPLICAS = "${mwDefaults.DEV1_Resource_Defaults.replicas}"  

          // MULE_VERSION = "${mwDefaults.deployment_Params_Defaults.muleVersion}"
          // RTF_PROVIDER = "${mwDefaults.deployment_Params_Defaults.provider}"
          // SKIP_DEPLOY_VERIFY = "${mwDefaults.deployment_Params_Defaults.skipDeployVerification}"

          // ENFORCE_REPLICAS_ACROSS_NODES = "${mwDefaults.deployment_Params_Defaults.enforceReplicasAcrossNodes}"
          // UPDATE_STRATEGY = "${mwDefaults.deployment_Params_Defaults.updateStrategy}"
          // CLUSTERED = "${mwDefaults.deployment_Params_Defaults.clustered}"
          // FORWARD_SSL_SESSION = "${mwDefaults.deployment_Params_Defaults.forwardSSLSession}"
          // LAST_MILE_SECURITY = "${mwDefaults.deployment_Params_Defaults.lastMileSecurity}"
          // PERSISTENT_OBJECT_STORE = "${mwDefaults.deployment_Params_Defaults.persistentObjectStore}"          

          APP_NAME = "${PORTFOLIO_NAME_LOWER}-${PROJECT}-${ANYPOINT_ENV}"           
        }
        steps {
          script {
            //def appName = "${PORTFOLIO_NAME_LOWER}-${pipelineParams.projectName}-${ANYPOINT_ENV}"
            println "App Name: ${APP_NAME}"

            def deployParams = mwDefaults.deployment_Params_Defaults

            println "deployParams1: ${deployParams}"

            deployUtils.getDeploymentConfigs(deployParams,"${PORTFOLIO_NAME_LOWER}","${ANYPOINT_ENV}","${PROJECT}")

            println "deployParams2: ${deployParams}"

            def clusters = deployParams['CLUSTERS']
            //def mvnArgs = "${mwDefaults.mvnArgs}"
            //def pEnv = mwDefaults.portFolio_Env_Mappings["${PORTFOLIO_NAME}"]
            //println "Cluster: " + pEnv["${MULE_ENV}"][0]
            
            //println "RTF Cluster:  ${RTF_CLUSTER_NAME}"

            //println "Cluster: " + cluster
            
            withCredentials([
              usernamePassword(credentialsId: "${PORTFOLIO_NAME_LOWER}-${MULE_ENV}-creds", usernameVariable: 'ap_user', passwordVariable: 'ap_pass'),
              string(credentialsId: "${PORTFOLIO_NAME_LOWER}-${MULE_ENV}-key", variable: 'key')
              ]) {     
                for (cluster in clusters)   { 
                  println "Deploying to Cluster: " + cluster
                  //sh 'mvn $MVN_ARGS -Prtf mule:deploy -Dmule.artifact=dummy.jar -Danypoint.env.clientId=$ap_user -Danypoint.env.clientSecret=$ap_pass -Dsecret.key=$key -Drtf.cluster=$cluster'
                }
            }                     
          }
        }
      }

    }
  }
}
