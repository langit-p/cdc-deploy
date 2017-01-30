//Codedeploy Config
def application_name = 'CDC-deploy'
def file_name = 'langit.zip'

node {

	stage 'Checkout'
	deleteDir()
	checkout scm
	
	//upload to S3
	stage 'Upload'	
	withAWSCredential(deployRevisionToS3(file_name, application_name))
	

	//call the codedeploy
	stage 'Deploy'
	withAWSCredential(deployToCodeDeploy(file_name, application_name))

}

def deploy(String revisionPrefix, String applicationName) {
    def key = generateKey(revisionPrefix)
    deployRevisionToS3(key, applicationName)
    deployToCodeDeploy(key)
	waitDeployment(getDeploymentId())
}

def deployRevisionToS3(String key, String applicationName) {
	def description = "web application deployment"
	def codedeploy_s3_bucket = 'deployment-cdc'
	def codedeploy_source = 'target'
    sh("aws deploy push --application-name ${applicationName} --description '${description}' --ignore-hidden-files --s3-location s3://${codedeploy_s3_bucket}/${key} --source ${codedeploy_source} > .deployment_command ")
    sh("cat .deployment_command")
}

def deployToCodeDeploy(String key) {
	def description = "'web application deployment v${key}'" 
	def deployment_group = 'langit'
    def cmd = getCodedeployCommand()
    cmd = cmd.replace('<deployment-group-name>',deployment_group).replace('<deployment-config-name>','CodeDeployDefault.OneAtATime').replace('<description>', description)
	sh("aws ${cmd} > .deployment_id")
	sh("cat .deployment_id")
}

def waitDeployment(){
	def deploymentId = getDeploymentId()
    sh("aws deploy wait deployment-successful --deployment-id ${deploymentId}")    
	sh("aws deploy get-deployment --deployment-id ${deployment_id} > .deployment_result") 
	sh("cat .deployment_result")
}

def getCodedeployCommand() {
    def matcher = readFile('.deployment_command') =~ 'aws(.*)'
    matcher ? matcher[0][1] : null
}

def getDeploymentId() {
    def matcher = readFile('.deployment_id') =~ 'deploymentId\":\\s\"(.*)\"'
    matcher ? matcher[0][1] : null
}

def withAWSCredential(block){
    withCredentials([[$class          : 'AmazonWebServicesCredentialsBinding',
                      credentialsId   : 'AwsCDC',
                      accessKeyVariable: 'AWS_ACCESS_KEY_ID',
                      secretKeyVariable: 'AWS_SECRET_ACCESS_KEY']]) {
        block()
    }
}