package grails.buildtestdata

import grails.util.Environment
import grails.util.Holders
import org.apache.commons.logging.LogFactory
import org.springframework.beans.factory.annotation.Value

class TestDataConfigurationHolder {
    static log = LogFactory.getLog("grails.buildtestdata.TestDataConfigurationHolder")
    private static ConfigObject configFile
    private static Map sampleData

    static Map unitAdditionalBuild
    static Map abstractDefault

    private static ConfigSlurper configSlurper = new ConfigSlurper(Environment.current.name)

    static sampleDataIndexer = [:]

    static reset() {
        loadTestDataConfig()
    }

    static loadTestDataConfig() {
        Class testDataConfigClass = getDefaultTestDataConfigClass()

        if (testDataConfigClass) {
            configFile = configSlurper.parse(testDataConfigClass)
            setSampleData(configFile?.testDataConfig?.sampleData as Map)

            unitAdditionalBuild = configFile?.testDataConfig?.unitAdditionalBuild ?: [:]
            abstractDefault = configFile?.testDataConfig?.abstractDefault ?: [:]

            // If we have abstract defaults, automatically add transitive dependencies
            // for them since they may need to be built.
            abstractDefault?.each { key, value ->
                if (unitAdditionalBuild.containsKey(key)) {
                    unitAdditionalBuild[key] << value
                }
                else {
                    unitAdditionalBuild[key] = [value]
                }
            }

            log.debug "configFile loaded: ${configFile}"
        }
        else {
            setSampleData([:])
            unitAdditionalBuild = [:]
            abstractDefault = [:]
        }
    }

    static Class getDefaultTestDataConfigClass() {
        GroovyClassLoader classLoader = new GroovyClassLoader(TestDataConfigurationHolder.classLoader)
        String testDataConfig = Holders.config?.grails?.buildtestdata?.testDataConfig ?: 'TestDataConfig'
        try {
            return classLoader.loadClass(testDataConfig)
        }
        catch (ClassNotFoundException ignored) {
            log.warn "${testDataConfig}.groovy not found, build-test-data plugin proceeding without config file"
            return null
        }
    }

    static Map getSampleData() {
        if (sampleData == null) {
            loadTestDataConfig()
        }
        sampleData
    }

    static void setSampleData(Object configObject) {
        if (configObject instanceof String) {
            sampleData = configSlurper.parse(configObject)
        }
        else if (configObject instanceof Map) {
            sampleData = configObject
        }
        else {
            throw new IllegalArgumentException("TestDataConfigurationHolder.sampleData should be either a String or a Map")
        }
        sampleDataIndexer = [:]
    }

    static getConfigFor(String domainName) {
        return sampleData."$domainName"
    }

    static getUnitAdditionalBuildFor(String domainName) {
        unitAdditionalBuild."$domainName"
    }

    static getAbstractDefaultFor(String domainName) {
        abstractDefault."${domainName}"
    }

    static getConfigPropertyNames(String domainName) {
        return getConfigFor(domainName)?.keySet() ?: []
    }

    static getSuppliedPropertyValue(propertyValues, domainName, propertyName) {
        return retrievePropertyValue(propertyValues, sampleData."$domainName"?."$propertyName")
    }

    private static retrievePropertyValue(propertyValues, Closure closure) {
        return closure.maximumNumberOfParameters > 0 ? closure.call(propertyValues) : closure.call()
    }

    private static retrievePropertyValue(propertyValues, value) {
        return value
    }

    static Map<String, Object> getPropertyValues(domainName, propertyNames, Map propertyValues = [:]) {
        for (propertyName in propertyNames) {
            propertyValues[propertyName] = getSuppliedPropertyValue(propertyValues, domainName, propertyName)
        }
        return propertyValues
    }

    static String getDomainBasePackage() {
        return configFile?.testDataConfig?.domainBasePackage ?: null
    }
}
