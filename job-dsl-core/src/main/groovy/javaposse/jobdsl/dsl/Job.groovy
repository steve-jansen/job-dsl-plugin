package javaposse.jobdsl.dsl

import javaposse.jobdsl.dsl.helpers.AuthorizationContextHelper
import javaposse.jobdsl.dsl.helpers.BuildParametersContextHelper
import javaposse.jobdsl.dsl.helpers.Helper
import javaposse.jobdsl.dsl.helpers.MavenHelper
import javaposse.jobdsl.dsl.helpers.MultiScmContextHelper
import javaposse.jobdsl.dsl.helpers.promotions.PromotionsContextHelper
import javaposse.jobdsl.dsl.helpers.ScmContextHelper
import javaposse.jobdsl.dsl.helpers.publisher.PublisherContextHelper
import javaposse.jobdsl.dsl.helpers.step.StepContextHelper
import javaposse.jobdsl.dsl.helpers.toplevel.TopLevelHelper
import javaposse.jobdsl.dsl.helpers.triggers.TriggerContextHelper
import javaposse.jobdsl.dsl.helpers.wrapper.WrapperContextHelper
import javaposse.jobdsl.dsl.additional.AdditionalXmlConfig

/**
 * DSL Element representing a Jenkins Job
 *
 * @author jryan
 * @author aharmel-law
 */
public class Job extends XmlConfig {
    JobManagement jobManagement

    String templateName = null // Optional
    JobType type = null // Required
    List<AdditionalXmlConfig> additionalConfigs = []

    // The idea here is that we'll let the helpers define their own methods, without polluting this class too much
    // TODO Use some methodMissing to do some sort of dynamic lookup
    @Delegate AuthorizationContextHelper helperAuthorization
    @Delegate ScmContextHelper helperScm
    @Delegate TriggerContextHelper helperTrigger
    @Delegate WrapperContextHelper helperWrapper
    @Delegate StepContextHelper helperStep
    @Delegate PublisherContextHelper helperPublisher
    @Delegate MultiScmContextHelper helperMultiscm
    @Delegate TopLevelHelper helperTopLevel
    @Delegate MavenHelper helperMaven
    @Delegate BuildParametersContextHelper helperBuildParameters
    @Delegate PromotionsContextHelper helperPromotions

    public Job(JobManagement jobManagement, Map<String, Object> arguments=[:]) {
        super(XmlConfigType.JOB)
        this.jobManagement = jobManagement
        def typeArg = arguments['type']?:JobType.Freeform
        this.type = (typeArg instanceof JobType)?typeArg:JobType.find(typeArg)

        // Helpers
        helperAuthorization = new AuthorizationContextHelper(super.withXmlActions, type)
        helperScm = new ScmContextHelper(super.withXmlActions, type, jobManagement)
        helperMultiscm = new MultiScmContextHelper(super.withXmlActions, type, jobManagement)
        helperTrigger = new TriggerContextHelper(super.withXmlActions, type)
        helperWrapper = new WrapperContextHelper(super.withXmlActions, type, jobManagement)
        helperStep = new StepContextHelper(super.withXmlActions, type)
        helperPublisher = new PublisherContextHelper(super.withXmlActions, type)
        helperTopLevel = new TopLevelHelper(super.withXmlActions, type)
        helperMaven = new MavenHelper(super.withXmlActions, type)
        helperBuildParameters = new BuildParametersContextHelper(super.withXmlActions, type)
        helperPromotions = new PromotionsContextHelper(super.withXmlActions, additionalConfigs, type)
    }

    /**
     * Creates a new job configuration, based on the job template referenced by the parameter and stores this.
     * @param templateName the name of the template upon which to base the new job
     * @return a new graph of groovy.util.Node objects, representing the job configuration structure
     * @throws JobTemplateMissingException
     */
    def using(String templateName) throws JobTemplateMissingException {
        if (this.templateName != null) {
            throw new RuntimeException('Can only use "using" once')
        }
        this.templateName = templateName
    }

    def name(String name) {
        // TODO Validation
        this.name = name
    }

    def name(Closure nameClosure) {
        // TODO do we need a delegate?
        name(nameClosure.call().toString())
    }

    public Node getNode() {
        Node project = templateName==null?executeEmptyTemplate():executeUsing()
        return project
    }
    
    // TODO record which templates are used to generate jobs, so that they can be connected to this job
    private executeUsing() {
        String configXml
        try {
            configXml = jobManagement.getConfig(templateName)
            if (configXml==null) {
                throw new JobConfigurationNotFoundException()
            }
        } catch (JobConfigurationNotFoundException jcnfex) {
            throw new JobTemplateMissingException(templateName)
        }

        def templateNode = new XmlParser().parse(new StringReader(configXml))

        if (type != getJobType(templateNode)) {
            throw new JobTypeMismatchException(name, templateName);
        }

        // Clean up our own indication that a job is a template
        List<Node> seedJobProperties = templateNode.depthFirst().findAll { it.name() == 'javaposse.jobdsl.plugin.SeedJobsProperty' }
        seedJobProperties.each { Node node -> node.parent().remove(node) }

        return templateNode
    }

    private executeEmptyTemplate() {
        return new XmlParser().parse(new StringReader(getTemplate(type)))
    }

    private String getTemplate(JobType type) {
        // TODO Move this logic to the JobType Enum
        switch(type) {
            case JobType.Freeform: return emptyTemplate
            case JobType.Maven: return emptyMavenTemplate
            case JobType.Multijob: return emptyMultijobTemplate
        }
    }

    /**
     * Determines the job type from the given config XML.
     */
    private static JobType getJobType(Node node) {
        def nodeElement = node.name()
        return JobType.values().find { it.elementName == nodeElement }
    }

    def emptyTemplate = '''<?xml version='1.0' encoding='UTF-8'?>
<project>
  <actions/>
  <description></description>
  <keepDependencies>false</keepDependencies>
  <properties/>
  <scm class="hudson.scm.NullSCM"/>
  <canRoam>true</canRoam>
  <disabled>false</disabled>
  <blockBuildWhenDownstreamBuilding>false</blockBuildWhenDownstreamBuilding>
  <blockBuildWhenUpstreamBuilding>false</blockBuildWhenUpstreamBuilding>
  <triggers class="vector"/>
  <concurrentBuild>false</concurrentBuild>
  <builders/>
  <publishers/>
  <buildWrappers/>
</project>
'''

    def emptyMavenTemplate = '''<?xml version='1.0' encoding='UTF-8'?>
<maven2-moduleset>
  <actions/>
  <description></description>
  <keepDependencies>false</keepDependencies>
  <properties/>
  <scm class="hudson.scm.NullSCM"/>
  <canRoam>true</canRoam>
  <disabled>false</disabled>
  <blockBuildWhenDownstreamBuilding>false</blockBuildWhenDownstreamBuilding>
  <blockBuildWhenUpstreamBuilding>false</blockBuildWhenUpstreamBuilding>
  <triggers class="vector"/>
  <concurrentBuild>false</concurrentBuild>
  <aggregatorStyleBuild>true</aggregatorStyleBuild>
  <incrementalBuild>false</incrementalBuild>
  <perModuleEmail>false</perModuleEmail>
  <ignoreUpstremChanges>true</ignoreUpstremChanges>
  <archivingDisabled>false</archivingDisabled>
  <resolveDependencies>false</resolveDependencies>
  <processPlugins>false</processPlugins>
  <mavenValidationLevel>-1</mavenValidationLevel>
  <runHeadless>false</runHeadless>
  <publishers/>
  <buildWrappers/>
</maven2-moduleset>
'''

    def emptyMultijobTemplate = '''<?xml version='1.0' encoding='UTF-8'?>
<com.tikal.jenkins.plugins.multijob.MultiJobProject plugin="jenkins-multijob-plugin@1.8">
  <actions/>
  <description/>
  <keepDependencies>false</keepDependencies>
  <properties/>
  <scm class="hudson.scm.NullSCM"/>
  <canRoam>true</canRoam>
  <disabled>false</disabled>
  <blockBuildWhenDownstreamBuilding>false</blockBuildWhenDownstreamBuilding>
  <blockBuildWhenUpstreamBuilding>false</blockBuildWhenUpstreamBuilding>
  <triggers class="vector"/>
  <concurrentBuild>false</concurrentBuild>
  <builders/>
  <publishers/>
  <buildWrappers/>
</com.tikal.jenkins.plugins.multijob.MultiJobProject>
'''
}
