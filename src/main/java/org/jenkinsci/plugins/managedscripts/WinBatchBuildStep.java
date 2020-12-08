package org.jenkinsci.plugins.managedscripts;

import hudson.Extension;
import hudson.ExtensionList;
import hudson.FilePath;
import hudson.model.*;
import hudson.model.Queue;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tasks.CommandInterpreter;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import org.jenkinsci.lib.configprovider.ConfigProvider;
import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.plugins.configfiles.ConfigFiles;
import org.jenkinsci.plugins.managedscripts.WinBatchConfig.Arg;
import org.kohsuke.stapler.*;

import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * A project that uses this builder can choose a build step from a list of predefined windows batch files that are used as command line scripts.
 * <p>
 *
 * @author Dominik Bartholdi (imod)
 * @see hudson.tasks.BatchFile
 */
public class WinBatchBuildStep extends CommandInterpreter {

    private static final Logger LOGGER = Logger.getLogger(PowerShellBuildStep.class.getName());

    private final String[] buildStepArgs;
    private String content;

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
        public ScriptBuildStepArgs(boolean defineArgs, ArgValue[] buildStepArgs) {
            this.defineArgs = defineArgs;
            this.buildStepArgs = buildStepArgs == null ? new ArgValue[0] : Arrays.copyOf(buildStepArgs, buildStepArgs.length);
        }
    }

    /**
     * The constructor used at form submission
     *
     * @param buildStepId the Id of the config file
     * @param scriptBuildStepArgs whether to save the args and arg values (the boolean is required because of html form submission, which also sends hidden values)
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
     * @param buildStepId the Id of the config file
     * @param buildStepArgs list of arguments specified as buildStepargs
     */
    public WinBatchBuildStep(String buildStepId, String[] buildStepArgs) {
        super(buildStepId); // save buildStepId as command
        this.buildStepArgs = buildStepArgs == null ? new String[0] : Arrays.copyOf(buildStepArgs, buildStepArgs.length);
    }

    public String getBuildStepId() {
        return getCommand();
    }

    public String[] getBuildStepArgs() {
        String[] args = buildStepArgs == null ? new String[0] : buildStepArgs;
        return Arrays.copyOf(args, args.length);
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

        Executor executor = Executor.currentExecutor();
        if (executor != null) {
            Queue.Executable currentExecutable = executor.getCurrentExecutable();
            if (currentExecutable != null) {
                Config buildStepConfig = ConfigFiles.getByIdOrNull((Run<?, ?>) currentExecutable, getBuildStepId());
                if (buildStepConfig == null) {
                    throw new IllegalStateException(Messages.config_does_not_exist(getBuildStepId()));
                }
                return buildStepConfig.content + "\r\nexit %ERRORLEVEL%";
            } else {
                String msg = "current executable not accessable! can't get content of script: " + getBuildStepId();
                LOGGER.log(Level.SEVERE, msg);
                throw new RuntimeException(msg);
            }
        } else {
            String msg = "current executor not accessable! can't get content of script: " + getBuildStepId();
            LOGGER.log(Level.SEVERE, msg);
            throw new RuntimeException(msg);
        }

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
        public ListBoxModel doFillBuildStepIdItems(@AncestorInPath ItemGroup context) {
            List<Config> configsInContext = ConfigFiles.getConfigsInContext(context, WinBatchConfig.WinBatchConfigProvider.class);
            Collections.sort(configsInContext, new Comparator<Config>() {
                @Override
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
    }

    /**
         * gets the argument description to be displayed on the screen when selecting a config in the dropdown
     *
     * @param configId the config id to get the arguments description for
     * @return the description
     */
    private String getArgsDescription(@AncestorInPath Item context, String configId) {
        final WinBatchConfig config = ConfigFiles.getByIdOrNull(context, configId);
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
     * @param buildStepId the buildStepId
     * @return
     */
    public HttpResponse doCheckBuildStepId(StaplerRequest req, @AncestorInPath Item context, @QueryParameter String buildStepId) {
        final WinBatchConfig config = ConfigFiles.getByIdOrNull(context, buildStepId);
        if (config != null) {
            return DetailLinkDescription.getDescription(req, context, buildStepId, getArgsDescription(context, buildStepId));
        } else {
            return FormValidation.error("you must select a valid batch file");
        }
    }
}
