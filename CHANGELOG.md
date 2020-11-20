## Changelog

[![GitHub release](https://img.shields.io/github/release/jenkinsci/managed-scripts-plugin.svg?label=changelog)](https://github.com/jenkinsci/managed-scripts-plugin/releases/latest)

##### Versions newer than 1.4

See [GitHub Releases](https://github.com/jenkinsci/managed-scripts-plugin/releases)

##### 1.4 (August 15, 2017)
- fix [JENKINS-45399](https://issues.jenkins-ci.org/browse/JENKINS-45399) View selected scripts doesn't show correct script
- fix [JENKINS-42888](https://issues.jenkins-ci.org/browse/JENKINS-42888) Missing argument labels when adding managed script as build step

##### 1.3 (Dec. 27, 2016)
- update to latest [Config File Provider Plugin](https://plugins.jenkins.io/config-file-provider/) (1.5) and therefore now supports [CloudBees Folders Plugin](https://plugins.jenkins.io/cloudbees-folder/)
- the update of the config file provider plugin causes the configuration to be migrated to a new format and therefore a rollback to the former version will not be supported!

##### 1.2.4 (Sep. 10, 2016)
add support for custom script ids to ease reuse of scripts with pipeline integration of config-file-provider-plugin

##### 1.2.1 (May 18, 2015)
add support for tokenizing of shell script arguments; allows decomposing the given value of each argument into multiple arguments by splitting via whitespace.

##### 1.2 (May 11, 2015)
add support for PowerShell [PR #5](https://github.com/jenkinsci/managed-scripts-plugin/pull/5)

##### 1.1.2 (July 18, 2014)
integrate [PR #4](https://github.com/jenkinsci/managed-scripts-plugin/pull/4) set codemirror mode based on shebang line (thanks to domruf)
integrate [PR #3](https://github.com/jenkinsci/managed-scripts-plugin/pull/3) correct spelling mistake (thanks to stevehollaar)

##### 1.1.1 (Dec. 12, 2013)
integrate [pull #2](https://github.com/jenkinsci/managed-scripts-plugin/pull/2): show script argument labels (thanks to Ann Campbell)

##### 1.1 (May 19, 2013)
fix [JENKINS-18004](https://issues.jenkins-ci.org/browse/JENKINS-18004) add support for TokenMacros

##### 1.0.2 (March 10, 2013)
fix [JENKINS-15995](https://issues.jenkins-ci.org/browse/JENKINS-15995) form submission from within [Conditional BuildStep Plugin](https://plugins.jenkins.io/conditional-buildstep/)

##### 1.0.1 (Oct 11, 2012)
fix issue when Jenkins is running with a different root context - details of scripts could not be opened

##### 1.0 (15. April 2012)
New Feature [JENKINS-12365](https://issues.jenkins-ci.org/browse/JENKINS-12365): Add support for managed .bat scripts for Windows

##### 0.2.0 (20. Jan. 2012)
FIX [JENKINS-12387](https://issues.jenkins-ci.org/browse/JENKINS-12387) "Can't open build_step_template" breaking sporadically
FIX [JENKINS-12375](https://issues.jenkins-ci.org/browse/JENKINS-12375) managed scripts not found running jobs on several nodes at the same time (parallel)
FIX [JENKINS-12346](https://issues.jenkins-ci.org/browse/JENKINS-12346) when jenkins is running with a prefix managed-scripts.js loads from / instead of /prefix on the project configure page
FIX [JENKINS-12283](https://issues.jenkins-ci.org/browse/JENKINS-12283) Remove "build_step_template..." files from workspace after these were

##### 0.1.0 (31. Dec. 2011)
Fix [JENKINS-12260](https://issues.jenkins-ci.org/browse/JENKINS-12260) - wrong workspace determination

##### 0.0.1 (10. Nov. 2011)
initial
