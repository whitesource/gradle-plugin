package org.whitesource.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.tasks.TaskAction
import org.whitesource.agent.api.ChecksumUtils
import org.whitesource.agent.api.model.AgentProjectInfo
import org.whitesource.agent.api.model.Coordinates
import org.whitesource.agent.api.model.DependencyInfo
import org.whitesource.gradle.WhitesourceConfiguration

/**
 * @author Itai Marko
 */
class CollectProjectInfoTask extends DefaultTask {

    WhitesourceConfiguration wssConfig = project.whitesource

    @TaskAction
    def CollectProjectInfos() {
        logger.lifecycle("processing ${project.name}")
        def projectInfo = new AgentProjectInfo()
        projectInfo.setCoordinates(new Coordinates(null, project.name, null))
        if (project.parent)
            projectInfo.setParentCoordinates(new Coordinates(null, project.parent.name, null))

        if (wssConfig.includedProjects.size() > 1) {
            projectInfo.setProjectToken(wssConfig.projectTokens.get(project.name))
        } else {
            projectInfo.setProjectToken(wssConfig.projectToken)
        }

        def configurationsToInclude = project.configurations.findAll { thisProjConf ->
            thisProjConf.name in wssConfig.includedConfigurationNames || thisProjConf.name in wssConfig.includedConfigurations*.name
        }

        def addedSha1s = new HashSet<String>()
        configurationsToInclude*.resolvedConfiguration*.getFiles(wssConfig.dependencyFilter).flatten().each {
            def sha1 = ChecksumUtils.calculateSHA1(it)
            if (!addedSha1s.contains(sha1)) {
                def dependencyInfo = new DependencyInfo()
                // TODO populate actual artifactId
                dependencyInfo.setArtifactId(it.name)
                // TODO populate groupId and version
                dependencyInfo.setGroupId(it.name)
                dependencyInfo.setVersion(it.name)
                dependencyInfo.setSystemPath(it.absolutePath)
                dependencyInfo.setSha1(sha1)

                // TODO collect transitive dependencies
                dependencyInfo.getChildren().add()

                projectInfo.getDependencies().add(dependencyInfo)
                addedSha1s.add(sha1)
            }
        }

        project.projectInfos.add(projectInfo)
    }
}
