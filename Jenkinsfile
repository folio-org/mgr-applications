import org.folio.eurekaImage.EurekaImage
import org.jenkinsci.plugins.workflow.libs.Library

//cosmetic changes
@Library('pipelines-shared-library@RANCHER-1502') _
node('jenkins-agent-java17') {
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
}

