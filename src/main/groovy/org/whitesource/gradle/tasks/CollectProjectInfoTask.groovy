package org.whitesource.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.tasks.TaskAction
import org.whitesource.agent.api.ChecksumUtils
import org.whitesource.agent.api.model.AgentProjectInfo
import org.whitesource.agent.api.model.Coordinates
import org.whitesource.agent.api.model.DependencyInfo
import org.whitesource.agent.api.model.DependencyType
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

        configurationsToInclude*.resolvedConfiguration*.getFirstLevelModuleDependencies(wssConfig.dependencyFilter).flatten().each { dependency ->
            def resolvedDependency = (ResolvedDependency) dependency
            def info = getDependencyInfo(resolvedDependency, addedSha1s)
            projectInfo.getDependencies().add(info)
        }

        configurationsToInclude*.resolvedConfiguration*.getFiles(wssConfig.dependencyFilter).flatten().each { file ->
            def sha1 = ChecksumUtils.calculateSHA1(file)
            if (!addedSha1s.contains(sha1)) {
                def dependencyInfo = new DependencyInfo()
                dependencyInfo.setArtifactId(file.name)
                dependencyInfo.setFilename(file.name)
                dependencyInfo.setSystemPath(file.absolutePath)
                dependencyInfo.setSha1(sha1)
                dependencyInfo.setDependencyType(DependencyType.GRADLE)
                projectInfo.getDependencies().add(dependencyInfo)
                addedSha1s.add(sha1)
            }
        }

        project.projectInfos.add(projectInfo)
    }

    def getDependencyInfo(ResolvedDependency dependency, addedSha1s) {
        def dependencyInfo = new DependencyInfo()
        def artifact = dependency.getModuleArtifacts()[0]
        dependencyInfo.setGroupId(dependency.getModuleGroup())
        dependencyInfo.setArtifactId(dependency.getModuleName())
        dependencyInfo.setVersion(dependency.getModuleVersion())
        dependencyInfo.setDependencyType(DependencyType.GRADLE)
        if (artifact != null) {
            def file = artifact.getFile()
            dependencyInfo.setType(artifact.getType())
            dependencyInfo.setSha1(ChecksumUtils.calculateSHA1(file))
            dependencyInfo.setSystemPath(file.getAbsolutePath())
            dependencyInfo.setFilename(file.getName())
        } else {
            logger.warn("No resolved artifact found for " + dependency.getName())
            logger.warn("Sending GAV coordinates without Sha1...")
        }
        if (!addedSha1s.contains(dependencyInfo.getSha1())) {
            if (dependencyInfo.getSha1() != null) {
                addedSha1s.add(dependencyInfo.getSha1())
            }
            dependency.getChildren().each {
                def info = getDependencyInfo(it, addedSha1s)
                dependencyInfo.getChildren().add(info)
            }
        }
        return dependencyInfo
    }
}
