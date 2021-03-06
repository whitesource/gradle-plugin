package org.whitesource.gradle.tasks

import org.apache.commons.lang.StringUtils
import org.gradle.api.DefaultTask
import org.gradle.api.artifacts.ResolvedDependency
import org.gradle.api.tasks.TaskAction
import org.whitesource.agent.hash.ChecksumUtils
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

        def configurationsToInclude = project.configurations.findAll { thisProjectConfiguration ->
            thisProjectConfiguration.name in wssConfig.includedConfigurationNames || thisProjectConfiguration.name in wssConfig.includedConfigurations*.name
        }

        def addedSha1s = new HashSet<String>()

        if (wssConfig.useAndroidPlugin){
            // filtering out configurations without files' list
            def files = configurationsToInclude.stream().filter({ configuration -> try { return  configuration.getFiles() != null; }
            catch (IllegalStateException e){
                return  false;
            }})*.getFiles().flatten();
            /**
             * path\groupId\artifactId\version\sha1\filename
             * e.g
             * path\com.android.support.test\rules\1.0.1\afc664aefa2eb399dc8cce3e87d795fb0822a9a7\rules-1.0.1.aar
             */
            files.stream().distinct().each { file ->
                def sha1Path = file.getParentFile(); // removing the file name
                if (!addedSha1s.contains(sha1Path.getName())){
                    def dependencyInfo = new DependencyInfo();
                    dependencyInfo.setSha1(sha1Path.getName());
                    def versionPath = sha1Path.getParentFile(); // removing the sha1 value
                    dependencyInfo.setVersion(versionPath.getName());
                    def artifactIdPath = versionPath.getParentFile(); // removing the version
                    dependencyInfo.setArtifactId(artifactIdPath.getName());
                    def groupIdPath = artifactIdPath.getParentFile(); // removing the artifact id
                    dependencyInfo.setGroupId(groupIdPath.getName());
                    addedSha1s.add(sha1Path);
                    projectInfo.getDependencies().add(dependencyInfo);
                }
            }
        } else {
            configurationsToInclude*.resolvedConfiguration*.getFirstLevelModuleDependencies(wssConfig.dependencyFilter).flatten().each { dependency ->
                def resolvedDependency = (ResolvedDependency) dependency
                def info = getDependencyInfo(resolvedDependency, addedSha1s, 1)
                if (info.getGroupId() != null || info.getArtifactId() != null || info.getVersion() != null) {
                    logger.info("CollectProjectInfoTask:CollectProjectInfos - info.groupId = " + info.getGroupId());
                    projectInfo.getDependencies().add(info)
                } else {
                    logger.debug("CollectProjectInfoTask:CollectProjectInfos - didn't add " + info.getGroupId() + "." + info.getArtifactId() + ":" + info.getVersion())
                }
            }
            configurationsToInclude*.resolvedConfiguration*.getFiles(wssConfig.dependencyFilter).flatten().each { file ->
                def sha1 = ChecksumUtils.calculateSHA1(file)
                if (!addedSha1s.contains(sha1)) {
                    logger.debug("adding " + file.name + " without groupId")
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
        }

        project.projectInfos.add(projectInfo)
    }

    def getDependencyInfo(ResolvedDependency dependency, addedSha1s, level) {
        logger.debug("getDependencyInfo: level = " + level +", dependency.name = " + dependency.getName())
        level++
        def dependencyInfo = new DependencyInfo()
        try {
            def artifact = dependency.getAllModuleArtifacts()[0]
            if (artifact != null) {
                def file = artifact.getFile()
                def sha1 = ChecksumUtils.calculateSHA1(file)
                if (!addedSha1s.contains(sha1)) {
                    dependencyInfo.setGroupId(dependency.getModuleGroup())
                    dependencyInfo.setArtifactId(dependency.getModuleName())
                    dependencyInfo.setVersion(dependency.getModuleVersion())
                    dependencyInfo.setSha1(sha1)
                    addedSha1s.add(sha1)
                    dependency.getChildren().each {
                        def info = getDependencyInfo(it, addedSha1s, level)
                        if (info.getSha1() != null) {
                            dependencyInfo.getChildren().add(info)
                        }
                    }
                }
            } else {
                logger.debug("can't find artifact of " + dependency.getName())
            }
        } catch (Error e){
            logger.warn("Can't get dependency " + dependency.getName() + " module artifacts.  Error message: " + e.getMessage())
            logger.debug("stack trace: {}", e.getStackTrace())
        }
        return dependencyInfo
    }
}