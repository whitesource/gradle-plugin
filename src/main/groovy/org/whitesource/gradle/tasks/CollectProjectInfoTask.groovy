package org.whitesource.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.Project
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.ModuleConflictResolver
import org.gradle.api.internal.project.DefaultProject
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

        configurationsToInclude*.resolvedConfiguration*.getFirstLevelModuleDependencies(wssConfig.dependencyFilter).flatten().each { dependency ->
            def resolvedDependency = (ResolvedDependency) dependency
            def info = getDependencyInfo(resolvedDependency)
            projectInfo.getDependencies().add(info)
        }
        project.projectInfos.add(projectInfo)
    }

    def getDependencyInfo(ResolvedDependency dependency) {
        def dependencyInfo = new DependencyInfo()
        def sha1 = ChecksumUtils.calculateSHA1(dependency.moduleArtifacts[0].file)
        dependencyInfo.setGroupId(dependency.getModuleGroup())
        dependencyInfo.setArtifactId(dependency.getModuleName())
        dependencyInfo.setVersion(dependency.getModuleVersion())
        dependencyInfo.setSha1(sha1)
        dependency.getChildren().each {
            dependencyInfo.getChildren().add(getDependencyInfo(it))
        }
        return dependencyInfo
    }
}
