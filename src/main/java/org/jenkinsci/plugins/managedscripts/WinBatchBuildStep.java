package org.jenkinsci.plugins.managedscripts;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.model.AbstractProject;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tasks.CommandInterpreter;
import hudson.util.FormValidation;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Logger;

import org.jenkinsci.lib.configprovider.ConfigProvider;
import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.plugins.managedscripts.WinBatchConfig.Arg;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.bind.JavaScriptMethod;

/**
 * A project that uses this builder can choose a build step from a list of predefined windows batch files that are used as command line scripts.
 * <p>
 *
 * @author Dominik Bartholdi (imod)
 * @see hudson.tasks.BatchFile
 */
public class WinBatchBuildStep extends CommandInterpreter {

    private final String[] buildStepArgs;

    public static class ArgValue {
        public final String arg;

        @DataBoundConstructor
        public ArgValue(String arg) {
            this.arg = arg;
        }
    }

    public static class ScriptBuildStepArgs {
        public final boolean defineArgs;
        public final ArgValue[] buildStepArgs;

        @DataBoundConstructor
        public ScriptBuildStepArgs(boolean defineArgs, ArgValue[] buildStepArgs)
        {
            this.defineArgs = defineArgs;
            this.buildStepArgs = buildStepArgs;
        }
    }
    /**
     * The constructor used at form submission
     *
     * @param buildStepId
     *            the Id of the config file
     * @param scriptBuildStepArgs
     *            whether to save the args and arg values (the boolean is required because of html form submission, which also sends hidden values)
     */
    @DataBoundConstructor
    public WinBatchBuildStep(String buildStepId, ScriptBuildStepArgs scriptBuildStepArgs) {
        super(buildStepId);
        List<String> l = null;
        if (scriptBuildStepArgs != null && scriptBuildStepArgs.defineArgs
                && scriptBuildStepArgs.buildStepArgs != null) {
            l = new ArrayList<String>();
            for (ArgValue arg : scriptBuildStepArgs.buildStepArgs) {
                l.add(arg.arg);
            }
        }
        this.buildStepArgs = l == null ? null : l.toArray(new String[l.size()]);
    }

    /**
     * The constructor
     *
     * @param buildStepId
     *            the Id of the config file
     * @param buildStepArgs
     *            list of arguments specified as buildStepargs
     */
    public WinBatchBuildStep(String buildStepId, String[] buildStepArgs) {
        super(buildStepId); // save buildStepId as command
        this.buildStepArgs = buildStepArgs;
    }

    public String getBuildStepId() {
        return getCommand();
    }

    public String[] getBuildStepArgs() {
        return buildStepArgs;
    }

    @Override
    public String[] buildCommandLine(FilePath script) {

        List<String> cml = new ArrayList<String>();
        cml.add("cmd");
        cml.add("/c");
        cml.add("call");
        cml.add(script.getRemote());

        // Add additional parameters set by user
        if (buildStepArgs != null) {
            for (String arg : buildStepArgs) {
                cml.add(arg);
            }
        }

        // return new String[] { "cmd", "/c", "call", script.getRemote() };
        return (String[]) cml.toArray(new String[cml.size()]);
    }

    @Override
    protected String getContents() {
        Config buildStepConfig = getDescriptor().getBuildStepConfigById(getBuildStepId());
        if (buildStepConfig == null) {
            throw new IllegalStateException(Messages.config_does_not_exist(getBuildStepId()));
        }
        return buildStepConfig.content + "\r\nexit %ERRORLEVEL%";
    }

    @Override
    protected String getFileExtension() {
        return ".bat";
    }

    // Overridden for better type safety.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    /**
     * Descriptor for {@link WinBatchBuildStep}.
     */
    @Extension(ordinal = 55)
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        final Logger logger = Logger.getLogger(WinBatchBuildStep.class.getName());

        /**
         * Enables this builder for all kinds of projects.
         */
        @Override
        public boolean isApplicable(Class<? extends AbstractProject> aClass) {
            return true;
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        @Override
        public String getDisplayName() {
            return Messages.win_buildstep_name();
        }

        /**
         * Return all batch files (templates) that the user can choose from when creating a build step. Ordered by name.
         *
         * @return A collection of batch files of type {@link WinBatchConfig}.
         */
        public Collection<Config> getAvailableBuildTemplates() {
            List<Config> allConfigs = new ArrayList<Config>(getBuildStepConfigProvider().getAllConfigs());
            Collections.sort(allConfigs, new Comparator<Config>() {
                public int compare(Config o1, Config o2) {
                    return o1.name.compareTo(o2.name);
                }
            });
            return allConfigs;
        }

        /**
         * Returns a Config object for a given config file Id.
         *
         * @param id
         *            The Id of a config file.
         * @return If Id can be found a Config object that represents the given Id is returned. Otherwise null.
         */
        public WinBatchConfig getBuildStepConfigById(String id) {
            return (WinBatchConfig) getBuildStepConfigProvider().getConfigById(id);
        }

        /**
         * gets the argument description to be displayed on the screen when selecting a config in the dropdown
         *
         * @param configId
         *            the config id to get the arguments description for
         * @return the description
         */
        @JavaScriptMethod
        public String getArgsDescription(String configId) {
            final WinBatchConfig config = getBuildStepConfigById(configId);
            if (config != null) {
                if (config.args != null && !config.args.isEmpty()) {
                    StringBuilder sb = new StringBuilder("Required arguments: ");
                    int i = 1;
                    for (Iterator<Arg> iterator = config.args.iterator(); iterator.hasNext(); i++) {
                        Arg arg = iterator.next();
                        sb.append(i).append(". ").append(arg.name);
                        if (iterator.hasNext()) {
                            sb.append(" | ");
                        }
                    }
                    return sb.toString();
                } else {
                    return "No arguments required";
                }
            }
            return "please select a script!";
        }

        @JavaScriptMethod
        public List<Arg> getArgs(String configId) {
            final WinBatchConfig config = getBuildStepConfigById(configId);
            return config.args;
        }

        /**
         * validate that an existing config was chosen
         *
         * @param value
         *            the configId
         * @return
         */
        public FormValidation doCheckBuildStepId(@QueryParameter String buildStepId) {
            final WinBatchConfig config = getBuildStepConfigById(buildStepId);
            if (config != null) {
                return FormValidation.ok();
            } else {
                return FormValidation.error("you must select a valid batch file");
            }
        }

        private ConfigProvider getBuildStepConfigProvider() {
            ExtensionList<ConfigProvider> providers = ConfigProvider.all();
            return providers.get(WinBatchConfig.WinBatchConfigProvider.class);
        }

    }

}
