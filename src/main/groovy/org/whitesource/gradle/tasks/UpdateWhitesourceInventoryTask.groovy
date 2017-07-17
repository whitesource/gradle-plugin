package org.whitesource.gradle.tasks

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.tasks.StopExecutionException
import org.gradle.api.tasks.TaskAction
import org.whitesource.agent.api.dispatch.CheckPolicyComplianceResult
import org.whitesource.agent.api.dispatch.UpdateInventoryResult
import org.whitesource.agent.api.model.DependencyInfo
import org.whitesource.agent.client.WhitesourceService
import org.whitesource.gradle.WhitesourceConfiguration
import org.whitesource.agent.report.PolicyCheckReport

/**
 * @author Itai Marko
 */
class UpdateWhitesourceInventoryTask extends DefaultTask {

    private static final String AGENT_TYPE = 'gradle-plugin'
    private static final String AGENT_VERSION = '2.3.8'

    WhitesourceConfiguration wssConfig
    private WhitesourceService service

    @TaskAction
    def updateInventory() {
        try {
            debugProjectInfos()
            wssConfig = project.whitesource
            createService()

            boolean gotPolicyRejections = false
            if (wssConfig.checkPolicies) {
                gotPolicyRejections = checkPolicies()
            }

            if (!gotPolicyRejections || wssConfig.forceUpdate) {
                if (gotPolicyRejections) {
                    logger.lifecycle('The forceUpdate flag is set to true. Updating White Source despite policy violations')
                }
                sendUpdate()
            }

            if (gotPolicyRejections) {
                String exceptionMessage = 'whitesource plugin detected policy violation'
                throw wssConfig.failOnRejection ? new GradleException(exceptionMessage) : new StopExecutionException(exceptionMessage)
            }

        } catch (GradleException e) {
            throw e
        } catch (StopExecutionException e) {
            throw e
        } catch (Exception e) {
            if (wssConfig.failOnError) {
                throw new GradleException(e.getMessage(), e)
            } else {
                logger.warn("An error occurred while executing updateWhitesource task: $e.message Run again with failOnError = true for more info.")
                throw new StopExecutionException()
            }
        } finally {
            if (service != null) {
                service.shutdown()
            }
        }
    }

    private void createService() {
        service = new WhitesourceService(AGENT_TYPE, AGENT_VERSION, "0.8")
        configureProxy()
    }

    private void configureProxy() {
        def proxy = project.whitesource.proxy
        if (proxy.host) {
            service.getClient().setProxy(proxy.host, proxy.port.toInteger(), proxy.user, proxy.password)
        }
    }

    private boolean checkPolicies() {
        logger.lifecycle('Checking policies...')
        CheckPolicyComplianceResult result = service.checkPolicyCompliance(wssConfig.orgToken, wssConfig.productName, wssConfig.productVersion, project.projectInfos, wssConfig.forceCheckAllDependencies)
        if (wssConfig.reportsDirectory.exists() || wssConfig.reportsDirectory.mkdirs()) {
            logger.lifecycle('Generating policy check report')
            PolicyCheckReport report = new PolicyCheckReport(result)
            report.generate(wssConfig.reportsDirectory, false)
        } else {
            logger.lifecycle("Failed to create outputdirectory ${wssConfig.reportsDirectory}. Skipping policies check report.")
        }

        if (result.hasRejections()) {
            logger.error("Some dependencies were rejected by the organization's policies. See policy check report at ${wssConfig.reportsDirectory}")
        } else {
            logger.lifecycle("All dependencies conform with the organization's policies.")
        }

        return result.hasRejections()
    }

    private void sendUpdate() {
        logger.lifecycle('Sending updates to White Source')
        UpdateInventoryResult result = service.update(wssConfig.orgToken, wssConfig.requesterEmail, wssConfig.productName, wssConfig.productVersion, project.projectInfos)
        logResult(result)
    }

    private void logResult(UpdateInventoryResult result) {
        logger.lifecycle("Inventory update results for ${result.getOrganization()}")

        // newly created projects
        Collection<String> createdProjects = result.getCreatedProjects()
        if (createdProjects.isEmpty()) {
            logger.lifecycle('No new projects found.')
        } else {
            logger.lifecycle('Newly created projects:')
            for (String projectName : createdProjects) {
                logger.lifecycle("\t${projectName}")
            }
        }

        // updated projects
        Collection<String> updatedProjects = result.getUpdatedProjects()
        if (updatedProjects.isEmpty()) {
            logger.lifecycle('No projects were updated.')
        } else {
            logger.lifecycle('Updated projects:')
            for (String projectName : updatedProjects) {
                logger.lifecycle("\t${projectName}")
            }
        }
    }

    private void debugProjectInfos() {
        logger.debug("----------------- dumping projectInfos -----------------")
        logger.debug("Total number of projects : " + project.projectInfos.size())

        project.projectInfos.each {projectInfo ->
            logger.debug("Project coordiantes: " + projectInfo.getCoordinates().toString())
            logger.debug("Project parent coordiantes: " + (projectInfo.getParentCoordinates() == null ? "" : projectInfo.getParentCoordinates().toString()))
            logger.debug("Project token: " + projectInfo.getProjectToken())
            logger.debug("total # of dependencies: " + projectInfo.getDependencies().size())
            for (DependencyInfo info : projectInfo.getDependencies()) {
                logger.debug(info.toString() + " SHA-1: " + info.getSha1())
            }
        }
        logger.debug("----------------- dump finished -----------------")
    }
}
