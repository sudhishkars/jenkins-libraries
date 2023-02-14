def call(Map pipelineParams) {
  pipeline {
    agent any

    tools {
      maven "M3"
    }

    environment {
      //Anypoint Platform connected app credentials (clientId/secret) - appName: ci-cd-connected-app
      ANYPOINT_CONNECTED_APP_CREDENTIALS = credentials('MEETUP_ANYPOINT_CONNECTED_APP_CREDENTIALS')

      //Maven settings XML
      MAVEN_SETTINGS_XML = credentials('MEETUP_MAVEN_SETTINGS_XML')

      //GitHub variables
      GITHUB_BRANCH = "${pipelineParams.branch}"
      GITHUB_HOST = "github.com"
      GITHUB_REPO = "${pipelineParams.gitHubRepo}"
      GITHUB_URL = "git@${GITHUB_HOST}:${GITHUB_REPO}"
      GITHUB_SCM_SSH_CONNECTION = "scm:git:ssh://git@${GITHUB_HOST}/${GITHUB_REPO}"
      GITHUB_SCM_HTTPS_CONNECTION = "scm:git:https://git@${GITHUB_HOST}/${GITHUB_REPO}"
      GITHUB_SCM_URL = "https://${GITHUB_HOST}:${GITHUB_REPO}"
      GITHUB_CREDENTIALS_ID = "github-credentials"

      //Deployment variables
      DEPLOY_CH = 'true'
      CH_WORKER_TYPE = "${pipelineParams.workerType}"
      CH_WORKERS = "${pipelineParams.workers}"
      CH_REGION = "us-west-1"
      CH_MULE_VERSION = "${pipelineParams.muleVersion}"
      CH_APP_NAME = "${pipelineParams.chAppName}"
      VISUALIZER_LAYER = "${pipelineParams.visualizerLayer}"

      //Deployment using API - variables
      CH_APP_DOMAIN = "${CH_APP_NAME}"
      ANYPOINT_API_HOST = "https://anypoint.mulesoft.com"
      ANYPOINT_API_OAUTH = "${ANYPOINT_API_HOST}/accounts/api/v2/oauth2/token"
      ANYPOINT_API_LOGIN = "${ANYPOINT_API_HOST}/accounts/login"
      ANYPOINT_API_CLOUDHUB_DEPLOYMENT = "${ANYPOINT_API_HOST}/cloudhub/api/v2/applications"
      ANYPOINT_API_CLOUDHUB_REDEPLOYMENT = "${ANYPOINT_API_CLOUDHUB_DEPLOYMENT}/${CH_APP_DOMAIN}"
      //Fixed OrgId
      ORG_ID = "a35b03e8-73f1-4ecf-ab2d-a5fad589c825"
      ENVIRONMENT_ID = getCloudHubEnvironmentId("${MULE_ENV}")

      // Environment specific variables
      MULE_ENV = "${pipelineParams.muleEnv}"
      MULE_ENV_UPPER = "${MULE_ENV.toUpperCase()}"
      IS_PRODUCTION = "${MULE_ENV == 'prod' ? 'true' : 'false'}"
      RUN_TESTS = "${MULE_ENV == 'prod' ? 'false' : 'true'}"
      RELEASE_PREPARE = "${MULE_ENV == 'qa' ? 'true' : 'false'}"
      RELEASE_PERFORM = "${MULE_ENV == 'qa' ? 'true' : 'false'}"
      RUN_INTEGRATION_TESTS = "${MULE_ENV == 'qa' ? 'true' : 'false'}"
      CH_ENV = getCloudHubEnvironment("${MULE_ENV}")
      ANYPOINT_ENV_CREDENTIALS = credentials("MEETUP_ANYPOINT_${MULE_ENV_UPPER}_ENV_CREDENTIALS")
      MULE_KEY = credentials("MEETUP_MULE_KEY_${MULE_ENV_UPPER}")

      EXTRA_PROPS = "${pipelineParams.extraProperties}"
    }

    stages {

      stage('Checkout Source Code') {
        steps {
          deleteDir()
          script {
            def githubUrl = scm.userRemoteConfigs[0].url
            print "GitHub URL: " + githubUrl
            print "SCM Params: " + scm.userRemoteConfigs[0]

            checkout([$class: 'GitSCM', 
                          branches: [[name: "*/${GITHUB_BRANCH}"]], 
                          doGenerateSubmoduleConfigurations: false, 
                          extensions: [[$class: 'WipeWorkspace'], [$class: 'LocalBranch', localBranch: "${GITHUB_BRANCH}"]], 
                          submoduleCfg: [], 
                          userRemoteConfigs: [[credentialsId: "${GITHUB_CREDENTIALS_ID}", url: "${githubUrl}"]]])
          }
        }
      }
      stage('Compile') {
        environment {
          ANYPOINT_ACCESS_TOKEN = getAuthToken("${ANYPOINT_API_OAUTH}", "${ANYPOINT_CONNECTED_APP_CREDENTIALS_USR}", "${ANYPOINT_CONNECTED_APP_CREDENTIALS_PSW}") 
        }
        steps {
          sh """ mvn -s ${MAVEN_SETTINGS_XML} clean compile \
                         -Danypoint.authToken=${ANYPOINT_ACCESS_TOKEN} """
        }
      }

      stage('Test') {
        when {
          expression { "${RUN_TESTS}" == 'true' }
        }
        environment {
          ANYPOINT_ACCESS_TOKEN = getAuthToken("${ANYPOINT_API_OAUTH}", "${ANYPOINT_CONNECTED_APP_CREDENTIALS_USR}", "${ANYPOINT_CONNECTED_APP_CREDENTIALS_PSW}") 
        }
        steps {
          sh """ mvn -s ${MAVEN_SETTINGS_XML} test \
                        -Danypoint.authToken=${ANYPOINT_ACCESS_TOKEN} """
        }
      }

      stage('Publish Test results') {
        when {
          expression { "${RUN_TESTS}" == 'true' }
        }
        steps {
          junit allowEmptyResults: true,
          testResults: 'target/surefire-reports/*.xml'
          publishHTML([allowMissing: true, alwaysLinkToLastBuild: false, keepAll: false, reportDir: 'target/site/munit/coverage', reportFiles: 'summary.html', reportName: 'MUnit Coverage Report', reportTitles: ''])
        }
      }

      stage('Release prepare') {
        when {
          expression { "${RELEASE_PREPARE}" == 'true' }
        }
        environment {
          ANYPOINT_ACCESS_TOKEN = getAuthToken("${ANYPOINT_API_OAUTH}", "${ANYPOINT_CONNECTED_APP_CREDENTIALS_USR}", "${ANYPOINT_CONNECTED_APP_CREDENTIALS_PSW}") 
        }
        steps {
          sh """ mvn -s ${MAVEN_SETTINGS_XML} -B release:prepare \
                        -Darguments=-DskipTests \
                        -Danypoint.authToken=${ANYPOINT_ACCESS_TOKEN} \
                        -Dscm.connection=$GITHUB_SCM_SSH_CONNECTION \
                        -Dscm.developerConnection=$GITHUB_SCM_SSH_CONNECTION \
                        -Dscm.url=$GITHUB_SCM_URL """
        }
      }

      stage('Release perform') {
        when {
          expression { "${RELEASE_PERFORM}" == 'true' }
        }
        environment {
          ANYPOINT_ACCESS_TOKEN = getAuthToken("${ANYPOINT_API_OAUTH}", "${ANYPOINT_CONNECTED_APP_CREDENTIALS_USR}", "${ANYPOINT_CONNECTED_APP_CREDENTIALS_PSW}") 
        }
        steps {
          sh """ mvn -s ${MAVEN_SETTINGS_XML} -B release:perform \
                        -Darguments=-DskipTests \
                        -Danypoint.authToken=${ANYPOINT_ACCESS_TOKEN} \
                        -Dscm.connection=$GITHUB_SCM_SSH_CONNECTION \
                        -Dscm.developerConnection=$GITHUB_SCM_SSH_CONNECTION \
                        -Dscm.url=$GITHUB_SCM_URL """

        }
      }

      stage('Deploy to CloudHub after Release') {
        when {
          allOf { 
            expression { "${DEPLOY_CH}" == 'true' }
            expression { "${RELEASE_PERFORM}" == 'true' }
            expression { "${RELEASE_PREPARE}" == 'true' }
            expression { "${IS_PRODUCTION}" == 'false' }
          }
        }
        environment {
          ANYPOINT_ACCESS_TOKEN = getAuthToken("${ANYPOINT_API_OAUTH}", "${ANYPOINT_CONNECTED_APP_CREDENTIALS_USR}", "${ANYPOINT_CONNECTED_APP_CREDENTIALS_PSW}") 
        }
        steps {
          script {
            ARTIFACT_LOCATION = sh(script: 'find target -maxdepth 1 -name "*.jar"', returnStdout: true).trim()

            deploy("jar", "${ANYPOINT_API_CLOUDHUB_DEPLOYMENT}", "${ANYPOINT_API_CLOUDHUB_REDEPLOYMENT}", 
            "${ANYPOINT_ACCESS_TOKEN}", "${ENVIRONMENT_ID}", "${ORG_ID}", 
            "${ARTIFACT_LOCATION}", null, null,
            "${CH_APP_NAME}", "${CH_MULE_VERSION}", "${CH_REGION}", 
            "${CH_WORKERS}", "${CH_WORKER_TYPE}", "${CH_APP_DOMAIN}", 
            "${MULE_ENV}", "${MULE_KEY}", "${ANYPOINT_ENV_CREDENTIALS_USR}", 
            "${ANYPOINT_ENV_CREDENTIALS_PSW}", "${VISUALIZER_LAYER}", 
            "${EXTRA_PROPS}")
          }
        }
      }

      stage('Deploy to CloudHub - generating new package') {
        when {
          allOf { 
            expression { "${DEPLOY_CH}" == 'true' }
            expression { "${RELEASE_PERFORM}" == 'false' }
            expression { "${RELEASE_PREPARE}" == 'false' }
            expression { "${IS_PRODUCTION}" == 'false' }
          }
        }
        environment {
          ANYPOINT_ACCESS_TOKEN = getAuthToken("${ANYPOINT_API_OAUTH}", "${ANYPOINT_CONNECTED_APP_CREDENTIALS_USR}", "${ANYPOINT_CONNECTED_APP_CREDENTIALS_PSW}") 
        }
        steps {

          sh """ mvn -s ${MAVEN_SETTINGS_XML} package \
                        -DskipTests \
                        -Danypoint.authToken=${ANYPOINT_ACCESS_TOKEN} """

          script {
            ARTIFACT_LOCATION = sh(script: 'find target -maxdepth 1 -name "*.jar"', returnStdout: true).trim()

            deploy("jar", "${ANYPOINT_API_CLOUDHUB_DEPLOYMENT}", "${ANYPOINT_API_CLOUDHUB_REDEPLOYMENT}", 
            "${ANYPOINT_ACCESS_TOKEN}", "${ENVIRONMENT_ID}", "${ORG_ID}", 
            "${ARTIFACT_LOCATION}", null, null,
            "${CH_APP_NAME}", "${CH_MULE_VERSION}", "${CH_REGION}", 
            "${CH_WORKERS}", "${CH_WORKER_TYPE}", "${CH_APP_DOMAIN}", 
            "${MULE_ENV}", "${MULE_KEY}", "${ANYPOINT_ENV_CREDENTIALS_USR}", 
            "${ANYPOINT_ENV_CREDENTIALS_PSW}", "${VISUALIZER_LAYER}", 
            "${EXTRA_PROPS}")
          }
        }
      }

      stage('Deploy to CloudHub from Exchange binary') {
        when {
          allOf { 
            expression { "${DEPLOY_CH}" == 'true' }
            expression { "${IS_PRODUCTION}" == 'true' }
          }
        }
        environment {
          
          EXCHANGE_ARTIFACT_ID = "${CH_APP_NAME}-impl"
          EXCHANGE_ARTIFACT_VERSION = "${appVersion}"

          ANYPOINT_ACCESS_TOKEN = getAuthToken("${ANYPOINT_API_OAUTH}", "${ANYPOINT_CONNECTED_APP_CREDENTIALS_USR}", "${ANYPOINT_CONNECTED_APP_CREDENTIALS_PSW}") 
        }
        steps {
          script {

            deploy("exchange", "${ANYPOINT_API_CLOUDHUB_DEPLOYMENT}", "${ANYPOINT_API_CLOUDHUB_REDEPLOYMENT}", 
                  "${ANYPOINT_ACCESS_TOKEN}", "${ENVIRONMENT_ID}", "${ORG_ID}", 
                  null, "${EXCHANGE_ARTIFACT_ID}", "${EXCHANGE_ARTIFACT_VERSION}",
                  "${CH_APP_NAME}", "${CH_MULE_VERSION}", "${CH_REGION}", 
                  "${CH_WORKERS}", "${CH_WORKER_TYPE}", "${CH_APP_DOMAIN}", 
                  "${MULE_ENV}", "${MULE_KEY}", "${ANYPOINT_ENV_CREDENTIALS_USR}", 
                  "${ANYPOINT_ENV_CREDENTIALS_PSW}", "${VISUALIZER_LAYER}", 
                  "${EXTRA_PROPS}")
          }
        }
      }

    }
  }
}

def getAuthToken(String oAuthUrl, String clientId, String clientSecret) { 

  return sh (script: "curl \
    -s ${oAuthUrl} \
    -X POST \
    -H 'Content-Type: application/json' \
    -d '{\"grant_type\": \"client_credentials\", \"client_id\": \"${clientId}\", \"client_secret\": \"${clientSecret}\"}' \
    | sed -n 's|.*\"access_token\":\"\\([^\"]*\\)\".*|\\1|p'", returnStdout: true).trim()
}

def getCloudHubEnvironment(String env) {
  def environment = ''
  switch (env) {
    case 'dev':
      environment = 'Development'
      break
    case 'qa':
      environment = 'QA'
      break
    case 'prod':
      environment = 'Production'
      break
    default:
      environment = ''
      break
  }
  return environment
}

def getCloudHubEnvironmentId(String env) {
  def environmentId = ''
  switch (env) {
    case 'dev':
      environmentId = '77cda639-a2ee-419e-b228-17000867927e'
      break
    case 'qa':
      environmentId = '12f91035-7cc3-4fde-b7ac-2a3fea9b1947'
      break
    case 'prod':
      environmentId = '32fe7783-1ff2-4974-9ea9-3016da961ab0'
      break
    default:
      environmentId = ''
      break
  }
  return environmentId
}

def getExchangeDeploymentBody(java.util.Map applicationInfo, String orgId, String artifactId, String artifactVersion) {

  return [
      applicationInfo: applicationInfo,
      applicationSource: [
          source: "EXCHANGE",
          groupId: orgId,
          artifactId: artifactId,
          version: artifactVersion,
          organizationId: orgId
      ],
      autoStart: true
  ]
}


def getDeploymentBody(String fileName, String muleVersion, String region, String workers, String workerType, String appDomain, 
  java.util.Map defaultProps, java.util.Map extraProps) {

  def deploymentBody = [
      fileName: fileName,
      muleVersion: [
        version: muleVersion
      ],
      properties: defaultProps,
      region: region,
      monitoringEnabled:true,
      monitoringAutoRestart:true,
      workers: [
        amount: workers,
        type: [name: workerType]
     ],
     domain: appDomain
  ]

  if (extraProps) {
    deploymentBody.get('properties').putAll(extraProps)
  }

  return deploymentBody
}

def getDefaultProperties(String muleEnv, String muleKey, String environmentClientId, String environmentClientSecret, String visualizerLayer) {
  return [
    "anypoint.platform.config.analytics.agent.enabled": "true", 
    "mule.env": muleEnv,
    "mule.key": muleKey,
    "anypoint.platform.client_id": environmentClientId,
    "anypoint.platform.client_secret": environmentClientSecret,
    "anypoint.platform.visualizer.layer": visualizerLayer
  ]
}

def getExtraProperties(String extraPropsString) {

  def extraPropsMap = [:]

  if(extraPropsString != null && extraPropsString != "null" && extraPropsString != "") {

    extraPropsMap = evaluate(extraPropsString)

    extraPropsMap.keySet().each{
      def credentialId = extraPropsMap[it]
      echo "Credential ID located: ${credentialId}"

      withCredentials([string(credentialsId: "${credentialId}", variable: 'credentialValue')]) {
        echo "Replacing credential ID with value from the vault"
        extraPropsMap[it] = "${credentialValue}"
      }
    }
  }
  

  return extraPropsMap
}

def getApplicationInfo(String cloudHubUrl, String token, String environmentId, String orgId){
  return sh (script: "curl \
    -s -I \
    -o /dev/null \
    -w '%{http_code}' \
    -X GET ${cloudHubUrl} \
    -H 'Authorization: Bearer ${token}' \
    -H 'X-ANYPNT-ENV-ID: ${environmentId}' \
    -H 'X-ANYPNT-ORG-ID: ${orgId}' \
    -H 'Content-Type: application/json'", returnStdout: true)
}

def deployApplication(String cloudHubUrl, String token, String environmentId, String orgId, String appPath, String jsonBody){
  return sh (script: "curl \
    -s -i \
    -o /dev/null \
    -w '%{http_code}' \
    -X POST ${cloudHubUrl} \
    -H 'Authorization: Bearer ${token}' \
    -H 'X-ANYPNT-ENV-ID: ${environmentId}' \
    -H 'X-ANYPNT-ORG-ID: ${orgId}' \
    -H 'Content-Type: multipart/form-data' \
    -F 'file=@${appPath}' \
    -F 'autoStart=true' \
    -F 'appInfoJson=${jsonBody}'", returnStdout: true)
}

def redeployApplication(String cloudHubUrl, String token, String environmentId, String orgId, String appPath, String jsonBody){
  return sh (script: "curl \
    -s -i \
    -o /dev/null \
    -w '%{http_code}' \
    -X PUT ${cloudHubUrl} \
    -H 'Authorization: Bearer ${token}' \
    -H 'X-ANYPNT-ENV-ID: ${environmentId}' \
    -H 'X-ANYPNT-ORG-ID: ${orgId}' \
    -H 'Content-Type: multipart/form-data' \
    -F 'file=@${appPath}' \
    -F 'autoStart=true' \
    -F 'appInfoJson=${jsonBody}'", returnStdout: true)
}

def deployApplicationFromExchange(String cloudHubUrl, String token, String environmentId, String orgId, String jsonBody){
  return sh (script: "curl \
    -s -i \
    -o /dev/null \
    -w '%{http_code}' \
    -X POST ${cloudHubUrl} \
    -H 'Authorization: Bearer ${token}' \
    -H 'X-ANYPNT-ENV-ID: ${environmentId}' \
    -H 'X-ANYPNT-ORG-ID: ${orgId}' \
    -H 'Content-Type: application/json' \
    -d '${jsonBody}'", returnStdout: true)
}

def redeployApplicationFromExchange(String cloudHubUrl, String token, String environmentId, String orgId, String jsonBody){
  return sh (script: "curl \
    -s -i \
    -o /dev/null \
    -w '%{http_code}' \
    -X PUT ${cloudHubUrl} \
    -H 'Authorization: Bearer ${token}' \
    -H 'X-ANYPNT-ENV-ID: ${environmentId}' \
    -H 'X-ANYPNT-ORG-ID: ${orgId}' \
    -H 'Content-Type: application/json' \
    -d '${jsonBody}'", returnStdout: true)
}


def getApplicationStatus(String cloudHubUrl, String token, String environmentId, String orgId, String statusField) {

  return sh (script: "curl \
    -s \
    -X GET ${cloudHubUrl} \
    -H 'Authorization: Bearer ${token}' \
    -H 'X-ANYPNT-ENV-ID: ${environmentId}' \
    -H 'X-ANYPNT-ORG-ID: ${orgId}' \
    -H 'Content-Type: application/json' | /usr/local/bin/jq .${statusField} ", returnStdout: true).trim()
}

def waitForRedeploymentStatus(Integer expectedMaxDeploymentTime, Integer expectedInterval, String expectedStatus, 
  String statusField, String cloudHubUrl, String token, String environmentId, String orgId){

  def deploymentStatus = expectedStatus
  def maxDeploymentTime = expectedMaxDeploymentTime
  def interval = expectedInterval

  while ((deploymentStatus == expectedStatus) && maxDeploymentTime > 0) {
    echo "Waiting for re-deployment Update status ..."

    sleep(interval)

    maxDeploymentTime =  maxDeploymentTime - interval

    echo "Deployment time-check remaining: $maxDeploymentTime"

    deploymentStatus = getApplicationStatus(cloudHubUrl, token, environmentId, orgId, statusField)

    echo "$deploymentStatus"
  } 

  return deploymentStatus 
}

def waitForApplcationStatus(Integer expectedMaxDeploymentTime, Integer expectedInterval, String expectedStatus, 
  String statusField, String cloudHubUrl, String token, String environmentId, String orgId){

  def deploymentStatus = ""
  def maxDeploymentTime = expectedMaxDeploymentTime
  def interval = expectedInterval

  while ((deploymentStatus != expectedStatus) && maxDeploymentTime > 0) {
    echo "Waiting for application startup ..."

    sleep(interval)

    maxDeploymentTime =  maxDeploymentTime - interval

    echo "Application startup time-check remaining: $maxDeploymentTime"

    deploymentStatus = getApplicationStatus(cloudHubUrl, token, environmentId, orgId, statusField)

    echo "$deploymentStatus"
  } 

  return deploymentStatus 
}

def deploy(String mode, String cloudHubDeploymentUrl, String cloudHubRedeploymentUrl, String token, String environmentId, String orgId, 
  String jarPath, String artifactId, String artifactVersion,
  String fileName, String muleVersion, String region, 
  String workers, String workerType, String appDomain, 
  String muleEnv, String muleKey, String environmentClientId, 
  String environmentClientSecret, String visualizerLayer, 
  String extraPropertiesString) {

  def MODE_JAR = "jar"
  def MODE_EXCHANGE = "exchange"

  def STATUS_DEPLOYING = '"DEPLOYING"'
  def STATUS_STARTED = '"STARTED"'

  def FIELD_DEPLOYMENT_UPDATE_STATUS = "deploymentUpdateStatus"
  def FIELD = "status"

  def DEPLOYMENT_TIME_MAX_EXPECTED = 1000
  def DEPLOYMENT_TIME_INTERVAL = 30

  def APP_STARTUP_TIME_MAX_EXPECTED = 500
  def APP_STARTUP_TIME_INTERVAL = 30

  def ERROR_MSG_BAD_RESPONSE_CODE = "Response code different than 200 or 404, not able to decide for deployment/redeployment"
  def ERROR_MSG_BAD_MODE = "Wrong deployment mode, valid deployment modes are: jar, exchange"
  def ERROR_MSG_DEPLOYMENT_FAILURE = "Deployment failed, status code:"  
  def ERROR_MSG_DEPLOYMENT_STATUS_NOT_CONFIRMED ="Can't confirm deployment, verify manually"


  def defaultProps = getDefaultProperties(muleEnv, muleKey, environmentClientId, environmentClientSecret, visualizerLayer)

  def extraProps = getExtraProperties(extraPropertiesString)
  echo "Extra properties to be added: $extraProps"

  def deploymentBody = getDeploymentBody(fileName, muleVersion, region,  workers, workerType, appDomain, defaultProps, extraProps)
  
  if(mode == MODE_EXCHANGE) {
    echo "Deployment from EXCHANGE"
    deploymentBody = getExchangeDeploymentBody(deploymentBody, orgId, artifactId, artifactVersion)
  } else if (mode == MODE_JAR) {
    echo "Deployment from JAR FILE"
  } else {
    error(ERROR_MSG_BAD_MODE)
  }

  echo "$deploymentBody"

  def jsonDeploymentBody = groovy.json.JsonOutput.toJson(deploymentBody)
  jsonDeploymentBody = groovy.json.JsonOutput.prettyPrint(jsonDeploymentBody)
  echo "$jsonDeploymentBody"

  def cloudHubAppResponseCode = getApplicationInfo(cloudHubRedeploymentUrl, token, environmentId, orgId)

  echo "$cloudHubAppResponseCode"

  def deploymentResponse = ""
  def deploymentStatusResponse = ""

  if (cloudHubAppResponseCode == "200") {
    echo "App exists, doing a redeployment"

    if(mode == MODE_JAR) {
      deploymentResponse = redeployApplication(cloudHubRedeploymentUrl, token, environmentId, orgId, jarPath, jsonDeploymentBody)
    } else if (mode == MODE_EXCHANGE) {
      deploymentResponse = redeployApplicationFromExchange(cloudHubRedeploymentUrl, token, environmentId, orgId, jsonDeploymentBody)
    } else {
      error(ERROR_MSG_BAD_MODE)
    }

    if (deploymentResponse != "200") {
      error("${ERROR_MSG_DEPLOYMENT_FAILURE} ${deploymentResponse}")
    }

    deploymentStatusResponse = waitForRedeploymentStatus(DEPLOYMENT_TIME_MAX_EXPECTED, DEPLOYMENT_TIME_INTERVAL, STATUS_DEPLOYING, FIELD_DEPLOYMENT_UPDATE_STATUS, 
      cloudHubRedeploymentUrl, token, environmentId, orgId)

    if (deploymentStatusResponse == STATUS_DEPLOYING) {
      error(ERROR_MSG_DEPLOYMENT_STATUS_NOT_CONFIRMED)
    }

    deploymentStatusResponse = waitForApplcationStatus(APP_STARTUP_TIME_MAX_EXPECTED, APP_STARTUP_TIME_INTERVAL, STATUS_STARTED, FIELD, cloudHubRedeploymentUrl, 
      token, environmentId, orgId)

    if (deploymentStatusResponse != STATUS_STARTED) {
      error(ERROR_MSG_DEPLOYMENT_STATUS_NOT_CONFIRMED)
    }

  } else if (cloudHubAppResponseCode == "404") {
    echo "App does not exist, doing a deployment"

    if(mode == MODE_JAR) {
      deploymentResponse = deployApplication(cloudHubDeploymentUrl, token, environmentId, orgId, jarPath, jsonDeploymentBody)
    } else if (mode == MODE_EXCHANGE) {
      deploymentResponse = deployApplicationFromExchange(cloudHubDeploymentUrl, token, environmentId, orgId, jsonDeploymentBody)
    } else {
      error(ERROR_MSG_BAD_MODE)
    }

    if (deploymentResponse != "200") {
      error("${ERROR_MSG_DEPLOYMENT_FAILURE} ${deploymentResponse}")
    }
    
    deploymentStatusResponse = waitForApplcationStatus(APP_STARTUP_TIME_MAX_EXPECTED, APP_STARTUP_TIME_INTERVAL, STATUS_STARTED, FIELD, cloudHubRedeploymentUrl, 
      token, environmentId, orgId)

    if (deploymentStatusResponse != STATUS_STARTED) {
      error(ERROR_MSG_DEPLOYMENT_STATUS_NOT_CONFIRMED)
    }

  } else {
    error(ERROR_MSG_BAD_RESPONSE_CODE)
  }   
}
