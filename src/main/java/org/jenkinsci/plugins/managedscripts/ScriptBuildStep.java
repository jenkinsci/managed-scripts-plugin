package org.jenkinsci.plugins.managedscripts;

import hudson.*;
import hudson.model.*;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tasks.Shell;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import jenkins.model.Jenkins;
import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.plugins.configfiles.ConfigFiles;
import org.jenkinsci.plugins.managedscripts.ScriptConfig.Arg;
import org.jenkinsci.plugins.managedscripts.ScriptConfig.ScriptConfigProvider;
import org.jenkinsci.plugins.tokenmacro.TokenMacro;
import org.kohsuke.stapler.*;

import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

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

    private static Logger LOGGER = Logger.getLogger(ScriptBuildStep.class.getName());

    private final String buildStepId;
    private final String[] buildStepArgs;
    private final boolean tokenized;

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
        public final boolean tokenized;

        @DataBoundConstructor
        public ScriptBuildStepArgs(boolean defineArgs, ArgValue[] buildStepArgs, boolean tokenized) {
            this.defineArgs = defineArgs;
            this.buildStepArgs = buildStepArgs == null ? new ArgValue[0] : Arrays.copyOf(buildStepArgs, buildStepArgs.length);
            this.tokenized = tokenized;
        }
    }

    /**
     * The constructor used at form submission
     *
     * @param buildStepId         the Id of the config file
     * @param scriptBuildStepArgs whether to save the args and arg values (the boolean is required because of html form submission, which also sends hidden values)
     */
    @DataBoundConstructor
    public ScriptBuildStep(String buildStepId, ScriptBuildStepArgs scriptBuildStepArgs) {
        this.buildStepId = buildStepId;
        this.tokenized = scriptBuildStepArgs != null ? scriptBuildStepArgs.tokenized : false;
        List<String> l = null;
        if (scriptBuildStepArgs != null && scriptBuildStepArgs.defineArgs && scriptBuildStepArgs.buildStepArgs != null) {
            l = new ArrayList<String>();
            for (ArgValue arg : scriptBuildStepArgs.buildStepArgs) {
                l.add(arg.arg);
            }
        }
        this.buildStepArgs = l == null ? null : l.toArray(new String[l.size()]);
    }

    public ScriptBuildStep(String buildStepId, String[] buildStepArgs) {
        this.buildStepId = buildStepId;
        this.buildStepArgs = buildStepArgs == null ? new String[0] : Arrays.copyOf(buildStepArgs, buildStepArgs.length);
        this.tokenized = false;
    }

    public String getBuildStepId() {
        return buildStepId;
    }

    public String[] getBuildStepArgs() {
        String[] args = buildStepArgs == null ? new String[0] : buildStepArgs;
        return Arrays.copyOf(args, args.length);
    }

    public boolean isTokenized() {
        return tokenized;
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
        Config buildStepConfig = ConfigFiles.getByIdOrNull(build, buildStepId);
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

            if (workingDir != null) {
                /*
                 * Copying temporary file to remote execution host
                 */
                dest = workingDir.createTextTempFile("build_step_template", ".sh", data, false);
                LOGGER.log(Level.FINE, "Wrote script to " + Computer.currentComputer().getDisplayName() + ":" + dest.getRemote());

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
                    LOGGER.log(Level.FINE, "Using custom interpreter: " + interpreterLine);
                    // Add addition parameter to arguments list
                    for (int i = 1; i < interpreterElements.length; i++) {
                        args.add(interpreterElements[i]);
                    }
                } else {
                    // the shell executable is already configured for the Shell
                    // task, reuse it
                    final Shell.DescriptorImpl shellDescriptor = (Shell.DescriptorImpl) Jenkins.get().getDescriptor(Shell.class);
                    if (shellDescriptor != null) {
                        final String interpreter = shellDescriptor.getShellOrDefault(workingDir.getChannel());
                        args.add(interpreter);
                    }
                }

                args.add(dest.getRemote());

                // Add additional parameters set by user
                if (buildStepArgs != null) {
                    for (String arg : buildStepArgs) {
                        final String expanded = TokenMacro.expandAll(build, listener, arg, false, null);
                        if (tokenized) {
                            args.addTokenized(expanded);
                        } else {
                            args.add(expanded);
                        }
                    }
                }

            /*
             * Execute command remotely
             */
                int r = launcher.launch().cmds(args).envs(env).stderr(listener.getLogger()).stdout(listener.getLogger()).pwd(workingDir).join();
                returnValue = (r == 0);
            } else {
                LOGGER.log(Level.SEVERE, "no workspace precent, cant run script!");
                returnValue = false;
            }

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
        LOGGER.log(Level.FINE, "Finished script step");
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
        public ListBoxModel doFillBuildStepIdItems(@AncestorInPath ItemGroup context) {
            List<Config> configsInContext = ConfigFiles.getConfigsInContext(context, ScriptConfigProvider.class);
            Collections.sort(configsInContext, new Comparator<Config>() {
                public int compare(Config o1, Config o2) {
                    return o1.name.compareTo(o2.name);
                }
            });

            ListBoxModel items = new ListBoxModel();
            items.add("please select", "");
            for (Config config : configsInContext) {
                items.add(config.name, config.id);
            }
            return items;
        }


        /**
         * gets the argument description to be displayed on the screen when selecting a config in the dropdown
         *
         * @param configId the config id to get the arguments description for
         * @return the description
         */
        private String getArgsDescription(@AncestorInPath Item context, String configId) {
            final ScriptConfig config = ConfigFiles.getByIdOrNull(context, configId);
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
            return "please select a valid script!";
        }

        /**
         * validate that an existing config was chosen
         *
         * @param context     the context to search within for the configuration file with the given id
         * @param buildStepId the buildStepId
         * @return whether the config existts or not
         */
        public HttpResponse doCheckBuildStepId(StaplerRequest req, @AncestorInPath Item context, @QueryParameter String buildStepId) {
            final ScriptConfig config = ConfigFiles.getByIdOrNull(context, buildStepId);
            if (config != null) {
                return DetailLinkDescription.getDescription(req, context, buildStepId, getArgsDescription(context, buildStepId));
            } else {
                return FormValidation.error("you must select a valid script");
            }
        }


    }
}
