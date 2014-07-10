package javaposse.jobdsl.dsl

class FileJobManagement extends AbstractJobManagement {
    /**
     * Root of where to look for job config files
     */
    File root

    /**
     * Extension to append to job name when looking at the filesystem
     */
    String ext

    /**
     * map to store job parameters from System properties and
     * Environment variables.
     */
    protected Map params = [:]

    FileJobManagement(File root, String ext = null, PrintStream out = System.out) {
        super(out)
        this.root = root
        this.ext = ext ?: '.xml'
    }

    String getConfig(String jobName) throws JobConfigurationNotFoundException {

        if (jobName.isEmpty()) {
            return '''
<project>
  <actions/>
  <description/>
  <keepDependencies>false</keepDependencies>
  <properties/>
</project>'''
        }

        try {
            new File(root, jobName + ext).text
        } catch (IOException e) {
            throw new JobConfigurationNotFoundException(jobName)
        }
    }

    @Override
    boolean createOrUpdateConfig(String jobName, JobConfig config, boolean ignoreExisting)
        throws NameNotProvidedException, ConfigurationMissingException {
        validateUpdateArgs(jobName, config)

        new File(jobName + ext).write(config.mainConfig)
        for (JobConfigId configId : config.configs.keySet()) {
            if (configId.type == ItemType.ADDITIONAL) {
                new File(configId.type.toString()
                        + configId.relativePath.replace('/', '_')
                        + jobName + ext).write(config.getConfig(configId))
            }
        }
        true
    }

    @Override
    void createOrUpdateView(String viewName, String config, boolean ignoreExisting) {
        validateUpdateArgs(viewName, config)

        new File(viewName + ext).write(config)
    }

    @Override
    Map<String, String> getParameters() {
        params
    }

    @Override
    InputStream streamFileInWorkspace(String filePath) {
        new FileInputStream(new File(root, filePath))
    }

    @Override
    String readFileInWorkspace(String filePath) {
        new File(root, filePath).text
    }

    @Override
    void requireMinimumPluginVersion(String pluginShortName, String version) {
    }
}

