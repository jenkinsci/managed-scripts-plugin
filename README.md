Managed Scripts plugin for Jenkins
======================

[![Build Status](https://ci.jenkins.io/buildStatus/icon?job=Plugins/managed-scripts-plugin/master)](https://ci.jenkins.io/blue/organizations/jenkins/Plugins%2Fmanaged-scripts-plugin/branches/)
[![Jenkins Plugin](https://img.shields.io/jenkins/plugin/v/managed-scripts.svg)](https://plugins.jenkins.io/managed-scripts)
[![GitHub release](https://img.shields.io/github/release/jenkinsci/managed-scripts-plugin.svg?label=changelog)](https://github.com/jenkinsci/managed-scripts-plugin/releases/latest)
[![Jenkins Plugin Installs](https://img.shields.io/jenkins/plugin/i/managed-scripts.svg?color=blue)](https://plugins.jenkins.io/managed-scripts)

Managed scripts are shell scripts which are managed centrally by an administrator and can be referenced as a build step within jobs.

## Description
The scripts are managed by the [Config File Provider Plugin](https://plugins.jenkins.io/config-file-provider/), go to the "Manage Jenkins" screen and click on the "Managed files" item.

![](docs/images/managed_files_settings.jpg)

After installing the "Managed Scripts" plugin, you have a new option to create a managed script in the type list.

![](docs/images/managed_script_new.jpg)

Now you get to the screen to create your script. You're able to give it a name and a small description. In addition, you have the option to define a list of arguments you want to inform the users about. These arguments will be shown to the user as a hint and within the script you're able to reference these as if you would pass in arguments on the command line ($1, $2,...). Every environment variable from within a build will be visible to the script, too.

![](docs/images/edit_managed_config.jpg)

Within the job, a user is able to select the required script from a drop-down menu. The defined parameters are shown in respect to the selected script. The link enables the user to have a read-only look at the script.

![](docs/images/use_managed_script.jpg)


#### builds are currently executed on:

* [jenkins ci](https://ci.jenkins.io/blue/organizations/jenkins/Plugins%2Fmanaged-scripts-plugin/)
