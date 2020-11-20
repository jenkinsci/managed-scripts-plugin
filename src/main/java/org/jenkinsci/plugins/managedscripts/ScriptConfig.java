/**
 * 
 */
package org.jenkinsci.plugins.managedscripts;

import edu.umd.cs.findbugs.annotations.NonNull;
import hudson.Extension;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import jenkins.model.Jenkins;

import org.jenkinsci.lib.configprovider.AbstractConfigProviderImpl;
import org.jenkinsci.lib.configprovider.ConfigProvider;
import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.lib.configprovider.model.ContentType;
import org.jenkinsci.plugins.configfiles.maven.MavenSettingsConfig;
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author domi
 * 
 */
public class ScriptConfig extends Config {

    public final List<Arg> args;

    @DataBoundConstructor
    public ScriptConfig(String id, String name, String comment, String content, List<Arg> args) {
        super(id, name, comment, content);

        if (args != null) {
            List<Arg> filteredArgs = new ArrayList<ScriptConfig.Arg>();
            for (Arg arg : args) {
                if (arg.name != null && arg.name.trim().length() > 0) {
                    filteredArgs.add(arg);
                }
            }
            this.args = filteredArgs;
        } else {
            this.args = null;
        }
    }

    @Override
    public ConfigProvider getDescriptor() {
        return Jenkins.get().getDescriptorByType(ScriptConfigProvider.class);
    }

    public static class Arg implements Serializable {
        public final String name;

        @DataBoundConstructor
        public Arg(final String name) {
            this.name = name;
        }
    }

    @Extension(ordinal = 70)
    public static class ScriptConfigProvider extends AbstractConfigProviderImpl {

        public ScriptConfigProvider() {
            load();
        }

        @Override
        public ContentType getContentType() {
            return ContentType.DefinedType.HTML;
        }

        @Override
        public String getDisplayName() {
            return Messages.buildstep_provider_name();
        }

        @Override
        public Config newConfig() {
            String id = getProviderId() + System.currentTimeMillis();
            return new ScriptConfig(id, "Build Step", "", "echo \"hello world\"", null);
        }

        @NonNull
        @Override
        public Config newConfig(@NonNull String id) {
            return new ScriptConfig(id, "Build Step", "", "echo \"hello world\"", null);
        }

        @Override
        protected String getXmlFileName() {
            return "buildstep-config-files.xml";
        }

        // ======================
        // start stuff for backward compatibility
        protected transient String ID_PREFIX;


        static {
            Jenkins.XSTREAM.alias("org.jenkinsci.plugins.managedscripts.ScriptBuildStepConfigProvider", ScriptConfigProvider.class);
        }
        // end stuff for backward compatibility
        // ======================

    }

}
