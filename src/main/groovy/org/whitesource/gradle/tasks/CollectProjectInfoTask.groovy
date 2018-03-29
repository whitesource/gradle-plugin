package org.whitesource.gradle.tasks

import org.apache.commons.lang.StringUtils
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.internal.artifacts.configurations.DefaultConfiguration
import org.gradle.api.tasks.TaskAction
import org.whitesource.agent.api.ChecksumUtils
import org.whitesource.agent.api.model.AgentProjectInfo
import org.whitesource.agent.api.model.Coordinates
import org.whitesource.agent.api.model.DependencyInfo
import org.whitesource.agent.api.model.DependencyType
import org.whitesource.gradle.WhitesourceConfiguration
/**
 * @author Itai Marko
 * @author raz.nitzan
 */
class CollectProjectInfoTask extends DefaultTask {

    public static final String COMPILE_CLASSPATH = "CompileClasspath"
    WhitesourceConfiguration wssConfig = project.whitesource

    @TaskAction
    def CollectProjectInfos() {
        // set project names
        String projectName = null
        if (wssConfig.includedProjects.size() > 1) {
            if (!wssConfig.projectNames.isEmpty()) {
                projectName = wssConfig.projectNames.get(project.name)
            }
        } else {
            projectName = wssConfig.projectName
        }

        // validate project name
        if (StringUtils.isBlank(projectName)) {
            projectName = project.name
        }

        logger.lifecycle("Processing project ${project.name}")
        def projectInfo = new AgentProjectInfo()
        projectInfo.setCoordinates(new Coordinates(null, projectName, null))
        if (project.parent) {
            projectInfo.setParentCoordinates(new Coordinates(null, project.parent.name, null))
        }

        if (wssConfig.includedProjects.size() > 1) {
            projectInfo.setProjectToken(wssConfig.projectTokens.get(project.name))
        } else {
            projectInfo.setProjectToken(wssConfig.projectToken)
        }

        def configurationsToInclude = project.configurations.findAll { thisProjConf ->
            thisProjConf.name in wssConfig.includedConfigurationNames || thisProjConf.name in wssConfig.includedConfigurations*.name
        }

        def addedSha1s = new HashSet<String>()

        if (wssConfig.useAndroidPlugin){
            def configuration = configurationsToInclude.stream().filter({ configuration -> configuration.getName().indexOf(COMPILE_CLASSPATH) > -1 }).findFirst();
            Set<String> files = configuration.value.getFiles();
            files.stream().each {file ->
                String[] path = file.path.split("\\\\");
                int length = path.length;
                def sha1 = path[length-2];
                if (!addedSha1s.contains(sha1)) {
                    def dependencyInfo = new DependencyInfo()
                    dependencyInfo.setGroupId(path[length - 5]);
                    dependencyInfo.setArtifactId(path[length - 4])
                    dependencyInfo.setVersion(path[length - 3])
                    dependencyInfo.setSha1(sha1)
                    addedSha1s.add(sha1)
                    projectInfo.getDependencies().add(dependencyInfo)
                }
            }
        } else {
            configurationsToInclude*.resolvedConfiguration*.getFirstLevelModuleDependencies(wssConfig.dependencyFilter).flatten().each { dependency ->
                def resolvedDependency = (ResolvedDependency) dependency
                def info = getDependencyInfo(resolvedDependency, addedSha1s)
                if (info.getGroupId() != null || info.getArtifactId() != null || info.getVersion() != null) {
                    logger.lifecycle("CollectProjectInfoTask:CollectProjectInfos - info.groupId = " + info.getGroupId());
                    logger.lifecycle("CollectProjectInfoTask:CollectProjectInfos - projectInfo.getDependencies() = " + projectInfo.getDependencies());
                    projectInfo.getDependencies().add(info)
                }
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
                    logger.lifecycle("CollectProjectInfoTask:CollectProjectInfos - addedSha1s = " + addedSha1s);
                    logger.lifecycle("CollectProjectInfoTask:CollectProjectInfos - sha1 = " + sha1);
                    addedSha1s.add(sha1)
                }
            }
        }

        logger.lifecycle("CollectProjectInfoTask:CollectProjectInfos - project.projectInfos = " + project.projectInfos);
        project.projectInfos.add(projectInfo)
    }

    def getDependencyInfo(ResolvedDependency dependency, addedSha1s) {
        def dependencyInfo = new DependencyInfo()
        logger.lifecycle("CollectProjectInfoTask:getDependencyInfo - dependency.getAllModuleArtifacts() = " + dependency.getAllModuleArtifacts());
        def artifact = dependency.getAllModuleArtifacts()[0]
        if (artifact != null) {
            def file = artifact.getFile()
            def sha1 = ChecksumUtils.calculateSHA1(file)
            if (!addedSha1s.contains(sha1)) {
                dependencyInfo.setGroupId(dependency.getModuleGroup())
                dependencyInfo.setArtifactId(dependency.getModuleName())
                dependencyInfo.setVersion(dependency.getModuleVersion())
                dependencyInfo.setSha1(sha1)
                logger.lifecycle("CollectProjectInfoTask:getDependencyInfo - addedSha1s = " + addedSha1s);
                logger.lifecycle("CollectProjectInfoTask:getDependencyInfo - sha1 = " + sha1);
                addedSha1s.add(sha1)
                dependency.getChildren().each {
                    def info = getDependencyInfo(it, addedSha1s)
                    if (info.getSha1() != null) {
                        logger.lifecycle("CollectProjectInfoTask:getDependencyInfo - dependencyInfo.getChildren() = " + dependencyInfo.getChildren());
                        logger.lifecycle("CollectProjectInfoTask:getDependencyInfo - info = " + info);
                        dependencyInfo.getChildren().add(info)
                    }
                }
            }
        }
        return dependencyInfo
    }
}