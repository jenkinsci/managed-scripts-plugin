package org.jenkinsci.plugins.managedscripts;

import hudson.Extension;
import hudson.model.Item;
import hudson.model.ItemGroup;
import hudson.util.FormValidation;
import hudson.util.ListBoxModel;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import org.jenkinsci.Symbol;
import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.plugins.configfiles.ConfigFiles;
import org.jenkinsci.plugins.durabletask.DurableTask;
import org.jenkinsci.plugins.managedscripts.PowerShellConfig.Arg;
import org.jenkinsci.plugins.workflow.steps.durable_task.DurableTaskStep;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;
import org.kohsuke.stapler.HttpResponse;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;

/**
 * A project that uses this builder can choose a build step from a list of
 * predefined powershell files that are used as command line scripts.
 * <p>
 *
 * @author Michael DK Fowler
 */
public class ManagedPowerShellScriptStep extends DurableTaskStep {

    private final String scriptId;
    private String[] buildStepArgs;
    private ScriptBuildStepArgs scriptBuildStepArgs;

    public static class ArgValue implements Serializable {

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
     */
    @DataBoundConstructor
    public ManagedPowerShellScriptStep(String buildStepId) {
        if (buildStepId == null) {
            throw new IllegalArgumentException();
        }
        this.scriptId = buildStepId;
    }

    public ManagedPowerShellScriptStep(PowerShellBuildStep step) {
        if (step == null) {
            throw new IllegalArgumentException();
        }
        this.scriptId = step.getBuildStepId();
        this.buildStepArgs = step.getBuildStepArgs();
        if (this.buildStepArgs != null && this.buildStepArgs.length > 0) {
            ArgValue[] args = new ArgValue[buildStepArgs.length];
            for (int c = 0; c < buildStepArgs.length; c++) {
                args[c] = new ArgValue(buildStepArgs[c]);
            }
            this.scriptBuildStepArgs = new ScriptBuildStepArgs(true, args);
        }

    }

    public String getBuildStepId() {
        return scriptId;
    }

    public String[] getBuildStepArgs() {
        String[] args = buildStepArgs == null ? new String[0] : buildStepArgs;
        return Arrays.copyOf(args, args.length);
    }

    public ScriptBuildStepArgs getScriptBuildStepArgs() {
        return scriptBuildStepArgs;
    }

    @DataBoundSetter
    public void setScriptBuildStepArgs(ScriptBuildStepArgs scriptBuildStepArgs) {
        this.scriptBuildStepArgs = scriptBuildStepArgs;
        List<String> l = null;
        if (scriptBuildStepArgs != null && scriptBuildStepArgs.defineArgs
                && scriptBuildStepArgs.buildStepArgs != null) {
            l = new ArrayList<>();
            for (ArgValue arg : scriptBuildStepArgs.buildStepArgs) {
                l.add(arg.arg);
            }
        }
        this.buildStepArgs = l == null ? null : l.toArray(new String[l.size()]);
    }

    // Overridden for better type safety.
    @Override
    protected DurableTask task() {
        return new ManagedPowerShellScript(scriptId, this.scriptBuildStepArgs);
    }

    /**
     * Descriptor for {@link ManagedPowerShellScriptStep}.
     */
    @Symbol("managedbat")
    @Extension
    public static final class DescriptorImpl extends DurableTaskStepDescriptor {

        @Override
        public String getFunctionName() {
            return "managedpowershell";
        }

        /**
         * This human readable name is used in the configuration screen.
         */
        @Override
        public String getDisplayName() {
            return "Managed PowerShell Script";
        }

        /**
         * gets the argument description to be displayed on the screen when
         * selecting a config in the dropdown
         *
         * @param configId the config id to get the arguments description for
         * @return the description
         */
        private String getArgsDescription(@AncestorInPath Item context, String configId) {
            final PowerShellConfig config = ConfigFiles.getByIdOrNull(context, configId);
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
            final PowerShellConfig config = ConfigFiles.getByIdOrNull(context, buildStepId);
            if (config != null) {
                return DetailLinkDescription.getDescription(req, context, buildStepId, getArgsDescription(context, buildStepId));
            } else {
                return FormValidation.error("you must select a valid powershell file");
            }
        }

        /**
         * Return all powershell files (templates) that the user can choose from
         * when creating a build step. Ordered by name.
         *
         * @param context
         * @return A collection of powershell files of type
         * {@link PowerShellConfig}.
         */
        public ListBoxModel doFillBuildStepIdItems(@AncestorInPath ItemGroup context) {
            List<Config> configsInContext = ConfigFiles.getConfigsInContext(context, PowerShellConfig.PowerShellConfigProvider.class);
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
}
