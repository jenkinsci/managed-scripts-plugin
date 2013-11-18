package org.jenkinsci.plugins.managedscripts;

import hudson.EnvVars;
import hudson.Extension;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.Launcher;
import hudson.Util;
import hudson.model.BuildListener;
import hudson.model.AbstractBuild;
import hudson.model.AbstractProject;
import hudson.model.Computer;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tasks.Shell;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import hudson.util.VariableResolver;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

import jenkins.model.Jenkins;

import org.jenkinsci.lib.configprovider.ConfigProvider;
import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.plugins.managedscripts.ScriptConfig.Arg;
import org.jenkinsci.plugins.managedscripts.ScriptConfig.ScriptConfigProvider;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.bind.JavaScriptMethod;

/**
 * LibraryBuildStep {@link Builder}.
 * <p>
 * A project that uses this builder can choose a build step from a list of predefined config files that are uses as command line scripts. The hash-bang sequence at the beginning of each file is used
 * to determine the interpreter.
 * <p>
 *
 * @author Norman Baumann
 * @author Dominik Bartholdi (imod)
 */
public class ScriptBuildStep extends Builder {

    private static Logger log = Logger.getLogger(ScriptBuildStep.class.getName());

    private final String buildStepId;
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
    public ScriptBuildStep(String buildStepId, ScriptBuildStepArgs scriptBuildStepArgs)
    {
        this.buildStepId = buildStepId;
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

    public ScriptBuildStep(String buildStepId, String[] buildStepArgs) {
        this.buildStepId = buildStepId;
        this.buildStepArgs = buildStepArgs;
    }

    public String getBuildStepId() {
        return buildStepId;
    }

    public String[] getBuildStepArgs() {
        return buildStepArgs;
    }

    /**
     * Perform the build step on the execution host.
     * <p>
     * Generates a temporary file and copies the content of the predefined config file (by using the buildStepId) into it. It then copies this file into the workspace directory of the execution host
     * and executes it.
     */
    @Override
    public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
        boolean returnValue = true;
        Config buildStepConfig = getDescriptor().getBuildStepConfigById(buildStepId);
        if (buildStepConfig == null) {
            listener.getLogger().println(Messages.config_does_not_exist(buildStepId));
            return false;
        }
        listener.getLogger().println("executing script '" + buildStepConfig.name + "'");
        FilePath dest = null;
        try {
            FilePath workingDir = build.getWorkspace();
            EnvVars env = build.getEnvironment(listener);
            String data = buildStepConfig.content;

            /*
             * Copying temporary file to remote execution host
             */
            dest = workingDir.createTextTempFile("build_step_template", ".sh", data, false);
            log.log(Level.FINE, "Wrote script to " + Computer.currentComputer().getDisplayName() + ":" + dest.getRemote());

            /*
             * Analyze interpreter line (and use the desired interpreter)
             */
            ArgumentListBuilder args = new ArgumentListBuilder();
            if (data.startsWith("#!")) {
                String interpreterLine = data.substring(2, data.indexOf("\n"));
                String[] interpreterElements = interpreterLine.split("\\s+");
                // Add interpreter to arguments list
                String interpreter = interpreterElements[0];
                args.add(interpreter);
                log.log(Level.FINE, "Using custom interpreter: " + interpreterLine);
                // Add addition parameter to arguments list
                for (int i = 1; i < interpreterElements.length; i++) {
                    args.add(interpreterElements[i]);
                }
            } else {
                // the shell executable is already configured for the Shell
                // task, reuse it
                final Shell.DescriptorImpl shellDescriptor = (Shell.DescriptorImpl) Jenkins.getInstance().getDescriptor(Shell.class);
                final String interpreter = shellDescriptor.getShellOrDefault(workingDir.getChannel());
                args.add(interpreter);
            }

            args.add(dest.getRemote());

            // Add additional parameters set by user
            if (buildStepArgs != null) {
                for (String arg : buildStepArgs) {
                    args.add(TokenMacro.expandAll(build, listener, arg, false, null));
                }
            }

            /*
             * Execute command remotely
             */
            int r = launcher.launch().cmds(args).envs(env).stderr(listener.getLogger()).stdout(listener.getLogger()).pwd(workingDir).join();
            returnValue = (r == 0);

        } catch (IOException e) {
            Util.displayIOException(e, listener);
            e.printStackTrace(listener.fatalError("Cannot create temporary script for '" + buildStepConfig.name + "'"));
            returnValue = false;
        } catch (Exception e) {
            e.printStackTrace(listener.fatalError("Caught exception while loading script '" + buildStepConfig.name + "'"));
            returnValue = false;
        } finally {
            try {
                if (dest != null && dest.exists()) {
                    dest.delete();
                }
            } catch (Exception e) {
                e.printStackTrace(listener.fatalError("Cannot remove temporary script file '" + dest.getRemote() + "'"));
                returnValue = false;
            }
        }
        log.log(Level.FINE, "Finished script step");
        return returnValue;
    }

    // Overridden for better type safety.
    @Override
    public DescriptorImpl getDescriptor() {
        return (DescriptorImpl) super.getDescriptor();
    }

    /**
     * Descriptor for {@link ScriptBuildStep}.
     */
    @Extension(ordinal = 50)
    public static final class DescriptorImpl extends BuildStepDescriptor<Builder> {
        final Logger logger = Logger.getLogger(ScriptBuildStep.class.getName());

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
            return Messages.buildstep_name();
        }

        /**
         * Return all config files (templates) that the user can choose from when creating a build step. Ordered by name.
         *
         * @return A collection of config files of type {@link ScriptConfig}.
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
        public ScriptConfig getBuildStepConfigById(String id) {
            return (ScriptConfig) getBuildStepConfigProvider().getConfigById(id);
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
            final ScriptConfig config = getBuildStepConfigById(configId);
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
            final ScriptConfig config = getBuildStepConfigById(configId);
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
            final ScriptConfig config = getBuildStepConfigById(buildStepId);
            if (config != null) {
                return FormValidation.ok();
            } else {
                return FormValidation.error("you must select a valid script");
            }
        }

        private ConfigProvider getBuildStepConfigProvider() {
            ExtensionList<ConfigProvider> providers = ConfigProvider.all();
            return providers.get(ScriptConfigProvider.class);
        }

    }
}
