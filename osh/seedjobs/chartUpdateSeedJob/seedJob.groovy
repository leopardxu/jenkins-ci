def project = GIT_REPO + "/" + GIT_PROJECT
def folderProject = project.replaceAll('/','-')
def contentsAPI = new URL("https://api.github.com/repos/${GIT_REPO}/${GIT_PROJECT}/contents")
def defaultGitURL = new URL("https://github.com/${GIT_REPO}/${GIT_PROJECT}")

listView("OSH") { 
     jobs {
        name('OSH')
        regex(/OSH.+/)
     }
     description('OpenStack-Helm CI')
     columns {
     status()
     weather()
     name()
     lastSuccess()
     lastFailure()
     lastDuration()
     buildButton()
  }
}

folder("OSH/OSH-ChartUpdate/${folderProject}"){
     displayName("OSH-ChartUpdate/${folderProject}")
}

def repositoryContents = new groovy.json.JsonSlurper().parse(contentsAPI.newReader())

def helmExclusions = ["helm-toolkit","doc","tools","dev",".github","tests"]

def properties = """withEnv(['export WORK_DIR=\$(pwd)', 
                             'export HOST_OS=\${ID}']) { 
                                  node('jenkins-lab-slave-changeme') { \n" +
                                      "properties([parameters([string(defaultValue:'ArtifactoryEnterprise', description:'',name:'SERVER_ID'),
                                         string(defaultValue: '0', description: '', name: 'PATCH_VERSION'),
                                         string(defaultValue: '1', description: '', name: 'MINOR_VERSION'),
                                         string(defaultValue: '0', description: '', name: 'MAJOR_VERSION'),
                                         string(defaultValue: 'https://github.com/slfletch/openstack-helm-1.git', 
                                         description: '', name: 'GIT_URL'), 
                                         string(defaultValue: 'slfletch/openstack-helm-1', description: '', name: 'GIT_REPO'), 
                                         string(defaultValue: 'master', description: '', name: 'GIT_BRANCH'),
                                         string(defaultValue: 'v2.3.1', description: '', name: 'HELM_VERSION'), 
                                         string(defaultValue: 'v1.6.2', description: '', name: 'KUBE_VERSION'), 
                                         string(defaultValue: 'v1.6', description: '', name: 'KUBEADM_IMAGE_VERSION'), 
                                         string(defaultValue: 'openstackhelm/kubeadm-aio:\$KUBEADM_IMAGE_VERSION', description: '', name: 'KUBEADM_IMAGE'), 
                                         string(defaultValue: '/home/jenkins/.kubeadm-aio/admin.conf', description: '', name: 'KUBE_CONFIG')]), 
                                         pipelineTriggers([gerrit(customUrl: '', gerritProjects: [[branches: [[compareType: 'PLAIN', pattern: '**']], 
                                             compareType: 'PLAIN', disableStrictForbiddenFileVerification: false, pattern: 'openstack/openstack-helm']], 
                                             serverName: 'OpenstackHelmGerrit', 
                                             skipVote: [onFailed: true, onNotBuilt: true, onSuccessful: true, onUnstable: true], 
                                             triggerOnEvents: [patchsetCreated(excludeDrafts: false, excludeNoCodeChange: true, excludeTrivialRebase: true), draftPublished()]) 
                                             ])]) \n"""

def checkout = "git '$defaultGitURL' \n " +
   """sh '#!/bin/bash
      set -x 
      export HOST_OS=ubuntu 
      echo \$HOST_OS 
      source \$WORKSPACE/tools/gate/funcs/helm.sh 
      helm_install 
      helm_serve'"""

repositoryContents.each {
      def lint=""
      def packaging=""
      def dirName = it.name
      if (it.type == "dir" && !helmExclusions.contains(dirName)){
        lint = lint +" LINT_RESULT = sh(returnStatus: true, script:'make lint-"+dirName+"') \n"
        lint = lint +" if(LINT_RESULT != 0){ RESULTS.add('"+dirName+" Linting failed.')} \n" 
    
        packaging = packaging +" PACKAGE_RESULT = sh(returnStatus: true, script:'make build-"+dirName+"') \n"
        packaging = packaging +" if(PACKAGE_RESULT != 0){ RESULTS.add('"+dirName+" Package build failed.')} \n" 
  
        
        def test = "TEST_RESULT = sh(returnStatus: true, script:'helm test '"+ dirName+"')} \n"
        
        def update = ""
        
        def ubuntu = "dir(env.WORKSPACE){ \n sh '#!/bin/bash \\n export INTEGRATION=aio \\n export INTEGRATION_TYPE=basic \\n  ./tools/gate/setup_gate.sh'} \n"
  
        def publish='def server = Artifactory.server "ArtifactoryEnterprise" \n'
        
        def uploadSpec = """{
              "files": [{
                   "pattern": \""""+dirName+"""*.tar.gz",
                   "target": \""""+dirName+"""/\$MAJOR_VERSION.\$MINOR_VERSION.\$PATCH_VERSION.\$BUILD_NUMBER/"""+dirName+"""-\$MAJOR_VERSION.\$MINOR_VERSION.\$PATCH_VERSION.\$BUILD_NUMBER.tar.gz"
              }]
        }"""
  
        publish = publish + "server.upload(\"\"\""+uploadSpec+"\"\"\")\n"
     
  
  
     def slack = """
     def message = ""
     for(anError in RESULTS) {
         message=message + anError +'\n'
     }
     if(message != ""){
           slackSend channel: '@staceyfletcher', message: message, tokenCredentialId: 'staceyfletcher'
     }\n"""
  
  pipelineJob("ThirdPartyGating/OSH-ChartUpdate/"+folderProject+"/HelmChart-"+dirName.replaceAll('/','-')) {
   /* triggers {
        gerrit {
            events {
                changeMerged()
                draftPublished()
            }
            project('test-project', '**')
            buildSuccessful(10, null)
        }
    }
    */
    scm{
      git('${GIT_URL}', '${GIT_BRANCH}', null)
    }
    definition {
        cps {     
            script(properties + 
  """
     stage('Checkout'){""" + checkout +"""
     }
     stage('Lint'){""" + lint + """
     }
     stage('Package'){""" + packaging +"""
     }
     stage('Update'){""" + update + """
     }
     stage('Test'){}
     stage('Publish'){""" + publish + """			
     }
     stage('Ubuntu'){""" + ubuntu +"""
     }
   }
  }""".stripIndent())
        }
    }
  }
 }
}

def lastSuccessfulBuild(passedBuilds, build) {
  if ((build != null) && (build.result != 'SUCCESS')) {
      passedBuilds.add(build)
      lastSuccessfulBuild(passedBuilds, build.getPreviousBuild())
    }
}


def getChangeLog(passedBuilds) {
  def log = ""
  for (int x = 0; x < passedBuilds.size(); x++) {
    def currentBuild = passedBuilds[x];
    def changeLogSets = currentBuild.rawBuild.changeSets
    for (int i = 0; i < changeLogSets.size(); i++) {
      def entries = changeLogSets[i].items
      for (int j = 0; j < entries.length; j++) {
        def entry = entries[j]
        log += "* ${entry.msg} by ${entry.author} \n"
        for(def aFile : entry.getAffectedFiles()){
          log+= aFile.getSrc() + "\n"+ aFile.getPath() +"\n"
        }
      }
    }
  }
  return log;
}