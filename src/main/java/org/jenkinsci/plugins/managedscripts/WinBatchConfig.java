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
import org.kohsuke.stapler.DataBoundConstructor;

/**
 * @author Dominik Bartholdi (imod)
 * 
 */
public class WinBatchConfig extends Config {

    public final List<Arg> args;

    @DataBoundConstructor
    public WinBatchConfig(String id, String name, String comment, String content, List<Arg> args) {
        super(id, name, comment, content);

        if (args != null) {
            List<Arg> filteredArgs = new ArrayList<WinBatchConfig.Arg>();
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
        return Jenkins.get().getDescriptorByType(WinBatchConfigProvider.class);
    }

    public static class Arg implements Serializable {
        public final String name;

        @DataBoundConstructor
        public Arg(final String name) {
            this.name = name;
        }
    }

    @Extension(ordinal = 70)
    public static class WinBatchConfigProvider extends AbstractConfigProviderImpl {

        public WinBatchConfigProvider() {
            load();
        }

        @Override
        public ContentType getContentType() {
            return ContentType.DefinedType.HTML;
        }

        @Override
        public String getDisplayName() {
            return Messages.win_buildstep_provider_name();
        }

        @Override
        public Config newConfig() {
            String id = getProviderId() + System.currentTimeMillis();
            return new WinBatchConfig(id, "Build Step", "", "echo hello", null);
        }

        @NonNull
        @Override
        public Config newConfig(@NonNull String id) {
            return new WinBatchConfig(id, "Build Step", "", "echo hello", null);
        }


    }

}
