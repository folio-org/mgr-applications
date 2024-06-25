import org.folio.eureka.EurekaImage
import org.jenkinsci.plugins.workflow.libs.Library

@Library('pipelines-shared-library@RANCHER-1361') _
node('jenkins-agent-java17-bigmem') {
  stage('Build Docker Image') {
    dir('mgr-applications') {
      EurekaImage image = new EurekaImage(this)
      image.setModuleName('mgr-applications')
      image.makeImage()
    }
  }
  input("Paused to decide: proceed or not...")
}
buildMvn {
  publishModDescriptor = false
  mvnDeploy = true
  buildNode = 'jenkins-agent-java17-bigmem'
}
