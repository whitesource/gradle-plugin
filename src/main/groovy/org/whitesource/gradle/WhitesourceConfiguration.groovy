package org.whitesource.gradle

import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.Dependency
import org.gradle.api.specs.Spec

/**
 * @author Itai Marko
 */
class WhitesourceConfiguration {

    String orgToken
    String productName
    String productVersion
    Set<Project> includedProjects = []
    Set<Configuration> includedConfigurations = []
    Set<String> includedConfigurationNames = []
    Spec<? super Dependency> dependencyFilter
    /*String*/boolean checkPolicies
    File reportsDirectory
    boolean failOnRejection = true
    boolean forceUpdate = false

    String projectToken
    Map<String, String> projectTokens = [:]

    String requesterEmail
    boolean failOnError = true


    ProxyConfiguration proxyConf

    void orgToken(String orgToken) {
        this.orgToken = orgToken
    }

    void productName(String productName) {
        this.productName = productName
    }

    void productVersion(String productVersion) {
        this.productVersion = productVersion
    }

    void includeProject(Project project) {
        this.includedProjects << project
    }

    void includeProjects(Collection<Project> projects) {
        this.includedProjects += projects
    }

    void includeConfiguration(Configuration configuration) {
        this.includedConfigurations << configuration
    }

    void includeConfiguration(String configurationName) {
        this.includedConfigurationNames << configurationName
    }

    void includeConfigurations(Collection<Configuration> configurations) {
        this.includedConfigurations += configurations
    }

    void dependencyFilter(Spec<? super Dependency> dependencyFilter) {
        this.dependencyFilter = dependencyFilter
    }

    void checkPolicies(/*String*/boolean checkPolicies) {
        this.checkPolicies = checkPolicies
    }

    void reportsDirectory(File reportsDirectory) {
        this.reportsDirectory = reportsDirectory
    }

    void failOnRejection(boolean failOnRejection) {
        this.failOnRejection = failOnRejection
    }

    void forceUpdate(boolean forceUpdate) {
        this.forceUpdate = forceUpdate
    }

    void host(String host) {
        this.proxyConf.host = host
    }

    void port(String port) {
        this.proxyConf.port = port
    }

    void user(String user) {
        this.proxyConf.user = user
    }

    void password(String password) {
        this.proxyConf.password = password
    }

    void projectToken(String projectToken) {
        this.projectToken = projectToken
    }

    void projectTokens(Map<String, String> projectTokens) {
        this.projectTokens = projectTokens
    }

    void requesterEmail(String requesterEmail) {
        this.requesterEmail = requesterEmail
    }

    void failOnError(boolean failOnError) {
        this.failOnError = failOnError
    }
}

class ProxyConfiguration {
    String host
    String port
    String user
    String password
}
