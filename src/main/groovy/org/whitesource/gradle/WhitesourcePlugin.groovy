package org.whitesource.gradle

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.artifacts.Dependency
import org.gradle.api.artifacts.ExternalDependency
import org.gradle.api.artifacts.FileCollectionDependency
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.specs.Spec
import org.whitesource.gradle.tasks.CollectProjectInfoTask
import org.whitesource.gradle.tasks.UpdateWhitesourceInventoryTask

/**
 * @author Itai Marko
 */
class WhitesourcePlugin implements Plugin<Project> {

    @Override
    void apply(Project project) {
        // create extension objects
        def wssConfig = project.extensions.create('whitesource', WhitesourceConfiguration)
        project.whitesource.extensions.create('proxy', ProxyConfiguration)

        // create an updateWhitesource task for the root project
        Task uwi = project.tasks.create(name: 'updateWhitesource', type: UpdateWhitesourceInventoryTask) {
            description = 'Sends WhiteSource an update of the libraries being used by each project'
            group = 'whitesource'
        }

        project.configure(project) {
            project.ext {
                projectInfos = []
                includeAllDependencies =
                        new Spec<Dependency>(){
                            @Override
                            boolean isSatisfiedBy(Dependency dep) {
                                return true
                            }
                        }
                defaultDependencyFilter =
                        new Spec<Dependency>(){
                            @Override
                            boolean isSatisfiedBy(Dependency dep) {
                                return dep instanceof ExternalDependency
                            }
                        }
                includeExternalOrFileDependencies =
                        new Spec<Dependency>(){
                            @Override
                            boolean isSatisfiedBy(Dependency dep) {
                                return dep instanceof ExternalDependency || dep instanceof FileCollectionDependency
                            }
                        }
            }

            afterEvaluate {
                //set defaults
                wssConfig.productName = wssConfig.productName ?: rootProject.name
                wssConfig.includedProjects = wssConfig.includedProjects ?: allprojects
                if (!(wssConfig.includedConfigurations || wssConfig.includedConfigurationNames)) {
                    wssConfig.includedConfigurationNames << 'runtime'
                }
                wssConfig.dependencyFilter = wssConfig.dependencyFilter ?: project.defaultDependencyFilter
                if (wssConfig.checkPolicies) {

                    if (!wssConfig.reportsDirectory) {
                        wssConfig.reportsDirectory = ((ProjectInternal)project).fileResolver.withBaseDir(project.buildDir).resolve('reports')
                    }
                }

                // create a collectProjectInfo task for each of the included projects
                def collectProjectInfoTaskName = 'collectProjectInfo'
                configure (wssConfig.includedProjects) {
                    def cpi = tasks.create(name: collectProjectInfoTaskName, type: CollectProjectInfoTask) {
                        description = 'Checks the libraries being used against the organizational policies'
                        group = 'whitesource'
                    }
                    // set the updateWhitesource task to depend on each collectProjectInfo task created
                    uwi.dependsOn cpi
                }
            }
        }
    }
}
