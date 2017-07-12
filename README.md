# IBM Workload Automation plug-in for IBM UrbanCode Deploy [![Build Status](https://travis-ci.org/IBM-UrbanCode/IBM-Workload-Automation-UCD.svg?branch=master)](https://travis-ci.org/IBM-UrbanCode/IBM-Workload-Automation-UCD)
---
Note: This is not the plug-in distributable! This is the source code. To find the installable plug-in, go to the plug-in page on the [IBM UrbanCode Plug-ins microsite](https://developer.ibm.com/urbancode/plugins).

### License
This plug-in is protected under the [Eclipse Public 1.0 License](http://www.eclipse.org/legal/epl-v10.html)

### Overview
The IBM Workload Automation plug-in is a facility provided to help you moving workload automation definitions from one environment to another, in a distributed or z/OS environment. You can download a job stream definition from the Dynamic Workload Console, then IBM UrbanCode Deploy applies the appropriate environment parameters for the importing process that is performed by the plug-in’s steps.

### Documentation
View the /doc folder for a details on the IBM Workload Automation plug-in's steps.

### Support
This plug-in is an open source project supported by the IBM UrbanCode Development Community. Installable plug-ins are available in the releases tab. Create a GitHub Issue or Pull Request for minor requests and bug fixes. Plug-ins built externally or modified with custom code are supported on a best-effort-basis using GitHub Issues.

### Locally Build the Plug-in
This open source plug-in uses Gradle as its build tool. [Install the latest version of Gradle](https://gradle.org/install) to build the plug-in locally. Build the plug-in by running the `gradle` command in the plug-in's root directory. The plug-in distributable will be placed under the `build/distributions` folder.
