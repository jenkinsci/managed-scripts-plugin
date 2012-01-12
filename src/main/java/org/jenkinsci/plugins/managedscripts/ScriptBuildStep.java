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
import hudson.model.Node;
import hudson.tasks.BuildStepDescriptor;
import hudson.tasks.Builder;
import hudson.tasks.Shell;
import hudson.util.ArgumentListBuilder;
import hudson.util.FormValidation;
import hudson.util.VariableResolver;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
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
import net.sf.json.JSONArray;
import net.sf.json.JSONObject;

import org.jenkinsci.lib.configprovider.ConfigProvider;
import org.jenkinsci.lib.configprovider.model.Config;
import org.jenkinsci.plugins.managedscripts.ScriptConfig.Arg;
import org.kohsuke.stapler.DataBoundConstructor;
import org.kohsuke.stapler.QueryParameter;
import org.kohsuke.stapler.StaplerRequest;
import org.kohsuke.stapler.bind.JavaScriptMethod;

/**
 * LibraryBuildStep {@link Builder}.
 * <p>
 * A project that uses this builder can choose a build step from a list of
 * predefined config files that are uses as command line scripts. The hash-bang
 * sequence at the beginning of each file is used to determine the interpreter.
 * <p>
 * 
 * @author Norman Baumann
 * @author Dominik Bartholdi (imod)
 */
public class ScriptBuildStep extends Builder {

	private static Logger log = Logger.getLogger(ScriptBuildStep.class.getName());

	private final String buildStepId;
	private final String[] buildStepArgs;

	/**
	 * The constructor
	 * 
	 * @param buildStepId
	 *            the Id of the config file
	 * @param buildStepArgs
	 *            list of arguments specified as buildStepargs
	 */
	@DataBoundConstructor
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

	private Launcher getLastBuiltLauncher(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
		AbstractProject<?, ?> project = build.getProject();
		Node lastBuiltOn = project.getLastBuiltOn();
		Launcher lastBuiltLauncher = launcher;
		if (lastBuiltOn != null) {
			lastBuiltLauncher = lastBuiltOn.createLauncher(listener);
		}

		return lastBuiltLauncher;
	}

	/**
	 * Perform the build step on the execution host.
	 * <p>
	 * Generates a temporary file and copies the content of the predefined
	 * config file (by using the buildStepId) into it. It then copies this file
	 * into the workspace directory of the execution host and executes it.
	 */
	@Override
	public boolean perform(AbstractBuild<?, ?> build, Launcher launcher, BuildListener listener) {
		boolean returnValue = true;
		Config buildStepConfig = getDescriptor().getBuildStepConfigById(buildStepId);
		if (buildStepConfig == null) {
			listener.getLogger().println("Cannot find script with Id '" + buildStepId + "'. Are you sure it exists?");
			return false;
		}
		listener.getLogger().println("executing script '" + buildStepId + "'");
		File tempFile = null;
		try {
			FilePath workingDir = build.getWorkspace();
			EnvVars env = build.getEnvironment(listener);
			Launcher lastBuiltLauncher = getLastBuiltLauncher(build, launcher, listener);
			String data = buildStepConfig.content;

			/*
			 * Create local temporary file and write script code into it
			 */
			tempFile = File.createTempFile("build_step_template", ".sh");
			BufferedWriter tempFileWriter = new BufferedWriter(new FileWriter(tempFile));
			tempFileWriter.write(data);
			tempFileWriter.close();

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
				listener.getLogger().println("Using custom interpreter: " + interpreterLine);
				// Add addition parameter to arguments list
				for (int i = 1; i < interpreterElements.length; i++) {
					args.add(interpreterElements[i]);
				}
				args.add(tempFile.getName());
			} else {
				// the shell executable is already configured for the Shell
				// task, reuse it
				final Shell.DescriptorImpl shellDescriptor = (Shell.DescriptorImpl) Jenkins.getInstance().getDescriptor(Shell.class);
				final String interpreter = shellDescriptor.getShellOrDefault(workingDir.getChannel());
				args.add(interpreter, tempFile.getName());
			}

			// Add additional parameters set by user
			if (buildStepArgs != null) {
				final VariableResolver<String> variableResolver = build.getBuildVariableResolver();
				for (String arg : buildStepArgs) {
					args.add(resolveVariable(variableResolver, arg));
				}
			}

			/*
			 * Copying temporary file to remote execution host
			 */
			FilePath source = new FilePath(tempFile);
			FilePath dest = new FilePath(Computer.currentComputer().getChannel(), workingDir + "/" + tempFile.getName());


			try {
				log.log(Level.FINE, "Copying temporary file to " + Computer.currentComputer().getHostName() + ":" + workingDir + "/" + tempFile.getName());
				source.copyTo(dest);
				/*
				 * Execute command remotely
				 */
				listener.getLogger().println("Executing temp file '" + tempFile.getPath() + "'");
				int r = lastBuiltLauncher.launch().cmds(args).envs(env).stderr(listener.getLogger()).stdout(listener.getLogger()).pwd(workingDir).join();
				returnValue = (r == 0);
			} finally {

				try {
					dest.delete();
				} catch (Exception e) {
					e.printStackTrace(listener.fatalError("Cannot remove temporary script file '" + dest.getName() + "'"));
					returnValue = false;
				}
			}


		} catch (IOException e) {
			Util.displayIOException(e, listener);
			e.printStackTrace(listener.fatalError("Cannot create temporary script '" + buildStepConfig.name + "'"));
			returnValue = false;
		} catch (Exception e) {
			e.printStackTrace(listener.fatalError("Caught exception while loading script '" + buildStepConfig.name + "'"));
			returnValue = false;
		} finally {
			if (tempFile != null) {
				try {
					tempFile.delete();
				} catch (Exception e) {
					e.printStackTrace(listener.fatalError("Cannot remove temporary script file '" + tempFile.getName() + "'"));
					returnValue = false;
				}
			}
		}
		log.log(Level.FINE, "finished script step");
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
		 * Return all config files (templates) that the user can choose from
		 * when creating a build step. Ordered by name.
		 * 
		 * @return A collection of config files of type
		 *         {@link ScriptBuildStepConfigProvider}.
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
		 * @return If Id can be found a Config object that represents the given
		 *         Id is returned. Otherwise null.
		 */
		public ScriptConfig getBuildStepConfigById(String id) {
			return (ScriptConfig) getBuildStepConfigProvider().getConfigById(id);
		}

		/**
		 * gets the argument description to be displayed on the screen when
		 * selecting a config in the dropdown
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

		private ScriptBuildStepConfigProvider getBuildStepConfigProvider() {
			ExtensionList<ConfigProvider> providers = ConfigProvider.all();
			return providers.get(ScriptBuildStepConfigProvider.class);
		}

		/**
		 * Creates a new instance of LibraryBuildStep.
		 * 
		 * @param req
		 *            The web request as initialized by the user.
		 * @param json
		 *            A JSON object representing the users input.
		 * @return A LibraryBuildStep instance.
		 */
		@Override
		public ScriptBuildStep newInstance(StaplerRequest req, JSONObject json) {
			logger.log(Level.FINE, "New instance of LibraryBuildStep requested with JSON data:");
			logger.log(Level.FINE, json.toString(2));

			String id = json.getString("buildStepId");
			final JSONObject definedArgs = json.getJSONObject("defineArgs");
			if (!definedArgs.isNullObject()) {
				JSONObject argsObj = definedArgs.optJSONObject("buildStepArgs");
				if (argsObj == null) {
					JSONArray argsArrayObj = definedArgs.optJSONArray("buildStepArgs");
					String[] args = null;
					if (argsArrayObj != null) {
						Iterator<JSONObject> arguments = argsArrayObj.iterator();
						args = new String[argsArrayObj.size()];
						int i = 0;
						while (arguments.hasNext()) {
							args[i++] = arguments.next().getString("arg");
						}
					}
					return new ScriptBuildStep(id, args);
				} else {
					String[] args = new String[1];
					args[0] = argsObj.getString("arg");
					return new ScriptBuildStep(id, args);
				}
			} else {
				return new ScriptBuildStep(id, null);
			}
		}
	}

	/**
	 * Checks whether the given parameter is a build parameter and if so,
	 * returns the value of it.
	 * 
	 * @param variableResolver
	 *            resolver to be used
	 * @param potentalVariable
	 *            the potential variable string. The string will be treated as
	 *            variable, if it follows this pattern: ${XXX}
	 * @return value of the build parameter or the origin passed string
	 */
	private String resolveVariable(VariableResolver<String> variableResolver, String potentalVariable) {
		String value = potentalVariable;
		if (potentalVariable != null) {
			if (potentalVariable.startsWith("${") && potentalVariable.endsWith("}")) {
				value = potentalVariable.substring(2, potentalVariable.length() - 1);
				value = variableResolver.resolve(value);
				log.log(Level.FINE, "resolve " + potentalVariable + " to " + value);
			}
		}
		return value;
	}
}
