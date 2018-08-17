/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.jenkinsci.plugins.managedscripts;
import hudson.Extension;
import hudson.model.ItemGroup;
import hudson.util.ListBoxModel;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.logging.Logger;
import org.jenkinsci.Symbol;
import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.plugins.ManagedBatchScript.ManagedBatchScript;
import org.jenkinsci.plugins.configfiles.ConfigFiles;
import org.jenkinsci.plugins.durabletask.DurableTask;
import org.jenkinsci.plugins.workflow.steps.durable_task.DurableTaskStep;
import org.kohsuke.stapler.AncestorInPath;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.DataBoundSetter;

/**
 *
 * @author MFowler
 */
public class ManagedBatchScriptStep extends DurableTaskStep{
    private final String scriptId;
    private String[] buildStepArgs;
    private ScriptBuildStepArgs scriptBuildStepArgs;
    private static final Logger LOGGER = Logger.getLogger(ManagedBatchScriptStep.class.getName());
    
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
    
    public String getBuildStepId() {
        return scriptId;
    }

    public String[] getBuildStepArgs() {
        String[] args = buildStepArgs == null ? new String[0] : buildStepArgs;
        return Arrays.copyOf(args, args.length);
    }
    
    public ScriptBuildStepArgs getScriptBuildStepArgs(){
        return scriptBuildStepArgs;
    }
    
    @DataBoundSetter
    public void setScriptBuildStepArgs(ScriptBuildStepArgs scriptBuildStepArgs){
       this.scriptBuildStepArgs= scriptBuildStepArgs;
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
    
    @DataBoundConstructor
    public ManagedBatchScriptStep(String buildStepId) {
        if (buildStepId == null) {
            throw new IllegalArgumentException();
        }
        this.scriptId = buildStepId;
    }
    
    public ManagedBatchScriptStep(WinBatchBuildStep step) {
        if (step == null) {
            throw new IllegalArgumentException();
        }
        this.scriptId = step.getBuildStepId();
        this.buildStepArgs = step.getBuildStepArgs();
        if (this.buildStepArgs!= null && this.buildStepArgs.length>0){
            ArgValue[] args=new ArgValue[buildStepArgs.length];
            for (int c=0; c<buildStepArgs.length;c++){
                args[c]=new ArgValue(buildStepArgs[c]);
            }
            this.scriptBuildStepArgs=new ScriptBuildStepArgs(true,args);
        }
        
    }
    
    @Override
    protected DurableTask task() {
        return new ManagedBatchScript(scriptId, buildStepArgs);
    }
    
    @Symbol("managedbat")
    @Extension
    public static final class DescriptorImpl extends DurableTaskStepDescriptor {

        @Override public String getDisplayName() {
            return "Managed Windows Batch Script";
        }

        @Override public String getFunctionName() {
            return "managedbat";
        }
        
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
}
