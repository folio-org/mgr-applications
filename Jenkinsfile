import org.folio.eureka.EurekaImage
import org.jenkinsci.plugins.workflow.libs.Library

@Library('pipelines-shared-library') _
node('jenkins-agent-java17-bigmem') {
  stage('Build Docker Image') {
    dir('mgr-applications') {
      EurekaImage image = new EurekaImage(this)
      image.setModuleName('mgr-applications')
      image.makeImage()
    }
  }
}
buildMvn {
  publishModDescriptor = false
  mvnDeploy = true
  buildNode = 'jenkins-agent-java17-bigmem'

  doDocker = {
    buildJavaDocker {
    publishMaster = true
//       healthChk = true
//       healthChkCmd = 'wget --no-verbose --tries=1 --spider http://localhost:8081/admin/health || exit 1'
    }
  }
}
