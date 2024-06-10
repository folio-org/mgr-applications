import org.folio.eurekaImage.EurekaImage
import org.jenkinsci.plugins.workflow.libs.Library

@Library('pipelines-shared-library@RANCHER-1502') _
stage('Build Docker Image') {
  dir('mgr-applications') {
    EurekaImage image = new EurekaImage(this)
    image.setModuleName('mgr-applications')
    image.makeImage()
  }
}
buildMvn {
  publishModDescriptor = false
  mvnDeploy = true
  buildNode = 'jenkins-agent-java17-bigmem'
}

