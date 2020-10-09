# Camel K SaaS Example
 
![Camel K CI](https://github.com/openshift-integration/camel-k-example-saas/workflows/Camel%20K%20CI/badge.svg)

This example demonstrates how to use Camel K to integrate Salesforce and ServiceNow, two popular SaaS providers. It requires Salesforce and ServiceNow login credentials to run the demo. It demonstrates a simple scenario where every new Salesforce Case must be copied to ServiceNow as an Incident and it's Incident Number cross-refrerenced back into Salesforce Case. 

## Before you begin

Make sure you check-out this repository from git and open it with [VSCode](https://code.visualstudio.com/).

Instructions are based on [VSCode Didact](https://github.com/redhat-developer/vscode-didact), so make sure it's installed
from the VSCode extensions marketplace.

From the VSCode UI, right-click on the `readme.didact.md` file and select "Didact: Start Didact tutorial from File". A new Didact tab will be opened in VS Code.

Make sure you've opened this readme file with Didact before jumping to the next section.

## Preparing the cluster

This example can be run on any OpenShift 4.3+ cluster or a local development instance (such as [CRC](https://github.com/code-ready/crc)). Ensure that you have a cluster available and login to it using the OpenShift `oc` command line tool.

You need to create a new project named `camel-saas` for running this example. This can be done directly from the OpenShift web console or by executing the command:

```
oc new-project camel-saas
```
([^ execute](didact://?commandId=vscode.didact.sendNamedTerminalAString&text=camelTerm$$oc%20new-project%20camel-saas&completion=New%20project%20creation. "Opens a new terminal and sends the command above"){.didact})

You need to install the Camel K operator in the `camel-saas` project. To do so, go to the OpenShift 4.x web console, login with a cluster admin account and use the OperatorHub menu item on the left to find and install **"Red Hat Integration - Camel K"**. You will be given the option to install it globally on the cluster or on a specific namespace.
If using a specific namespace, make sure you select the `camel-saas` project from the dropdown list.
This completes the installation of the Camel K operator (it may take a couple of minutes).

When the operator is installed, from the OpenShift Help menu ("?") at the top of the WebConsole, you can access the "Command Line Tools" page, where you can download the **"kamel"** CLI, that is required for running this example. The CLI must be installed in your system path.

Refer to the **"Red Hat Integration - Camel K"** documentation for a more detailed explanation of the installation steps for the operator and the CLI.

You can use the following section to check if your environment is configured properly.

## Checking requirements

<a href='didact://?commandId=vscode.didact.validateAllRequirements' title='Validate all requirements!'><button>Validate all Requirements at Once!</button></a>

**OpenShift CLI ("oc")**

The OpenShift CLI tool ("oc") will be used to interact with the OpenShift cluster.

[Check if the OpenShift CLI ("oc") is installed](didact://?commandId=vscode.didact.cliCommandSuccessful&text=oc-requirements-status$$oc%20help&completion=Checked%20oc%20tool%20availability "Tests to see if `oc help` returns a 0 return code"){.didact}

*Status: unknown*{#oc-requirements-status}

**Connection to an OpenShift cluster**

You need to connect to an OpenShift cluster in order to run the examples.

[Check if you're connected to an OpenShift cluster](didact://?commandId=vscode.didact.requirementCheck&text=cluster-requirements-status$$oc%20get%20project$$NAME&completion=OpenShift%20is%20connected. "Tests to see if `kamel version` returns a result"){.didact}

*Status: unknown*{#cluster-requirements-status}

**Apache Camel K CLI ("kamel")**

Apart from the support provided by the VS Code extension, you also need the Apache Camel K CLI ("kamel") in order to 
access all Camel K features.

[Check if the Apache Camel K CLI ("kamel") is installed](didact://?commandId=vscode.didact.requirementCheck&text=kamel-requirements-status$$kamel%20version$$Camel%20K%20Client&completion=Apache%20Camel%20K%20CLI%20is%20available%20on%20this%20system. "Tests to see if `kamel version` returns a result"){.didact}

*Status: unknown*{#kamel-requirements-status}

### Optional Requirements

The following requirements are optional. They don't prevent the execution of the demo, but may make it easier to follow.

**VS Code Extension Pack for Apache Camel**

The VS Code Extension Pack for Apache Camel by Red Hat provides a collection of useful tools for Apache Camel K developers,
such as code completion and integrated lifecycle management. They are **recommended** for the tutorial, but they are **not**
required.

You can install it from the VS Code Extensions marketplace.

[Check if the VS Code Extension Pack for Apache Camel by Red Hat is installed](didact://?commandId=vscode.didact.extensionRequirementCheck&text=extension-requirement-status$$redhat.apache-camel-extension-pack&completion=Camel%20extension%20pack%20is%20available%20on%20this%20system. "Checks the VS Code workspace to make sure the extension pack is installed"){.didact}

*Status: unknown*{#extension-requirement-status}

## 1. Preparing the project

We'll connect to the `camel-saas` project and check the installation status.

To change project, open a terminal tab and type the following command:

```
oc project camel-saas
```
([^ execute](didact://?commandId=vscode.didact.sendNamedTerminalAString&text=camelTerm$$oc%20project%20camel-saas&completion=Switch%20project%20to%20camel-saas. "Opens a new terminal and sends the command above"){.didact})

We should now check that the operator is installed. To do so, execute the following command on a terminal:

Upon successful creation, you should ensure that the Camel K operator is installed:

```
oc get csv
```
([^ execute](didact://?commandId=vscode.didact.sendNamedTerminalAString&text=camelTerm$$oc%20get%20csv&completion=Checking%20Cluster%20Service%20Versions. "Opens a new terminal and sends the command above"){.didact})

When Camel K is installed, you should find an entry related to `red-hat-camel-k-operator` in phase `Succeeded`.

You can now proceed to the next section.

## 2. SaaS integration

This repository contains a Camel K integration that subscribes to new Salesforce Case creation notifications and maps the event data to create a new Incident in ServiceNow. It also adds the new ServiceNow Incident `Number` as the `Engineering Req Number` field in the Salesforce Case. 

### 2.1. Creating a Kubernetes Secret from Salesforce and ServiceNow credentials

This repository contains a simple [secret-saas.properties](didact://?commandId=vscode.openFolder&projectFilePath=secret-saas.properties&completion=Opened%20the%secret-saas.properties%20file "Opens the secret-saas.properties file"){.didact} that can be used to generate a Kubernetes Secret with the Salesforce and ServiceNow credentials. 

After property values have been added to `secret-saas.properties`, create the secret using the command:

```
oc create secret generic secret-saas --from-file=secret-saas.properties
```
([^ execute](didact://?commandId=vscode.didact.sendNamedTerminalAString&text=camelTerm$$oc%20create%20secret%20generic%20secret-saas%20--from-file%3Dsecret-saas.properties&completion=secret%20%22secret-saas%22%20created. "Create a secret with Salesforce and ServiceNow credentials"){.didact})

### 2.2. Running SaaS integration

The integration is all contained in a single file named `SalesforceToServiceNow.java` ([open](didact://?commandId=vscode.openFolder&projectFilePath=SalesforceToServiceNow.java&completion=Opened%20the%20SalesforceToServiceNow.java%20file "Opens the SalesforceToServiceNow.java file"){.didact}).

> **Note:** the `SalesforceToServiceNow.java` file contains a simple integration that uses the `salesforce` and `servicenow` components.
> Dependency management is automatically handled by Camel K that imports all required libraries from the Camel
> catalog via code inspection. This means you can use all 300+ Camel components directly in your routes.

We're ready to run the integration on our `camel-saas` project in the cluster.

Use the following command to run it in "dev mode", in order to see the logs in the integration terminal:

```
kamel run SalesforceToServiceNow.java --dev
```
([^ execute](didact://?commandId=vscode.didact.sendNamedTerminalAString&text=camelTerm$$kamel%20run%20SalesforceToServiceNow.java%20--dev&completion=Camel%20K%20saas%20integration%20run%20in%20dev%20mode. "Opens a new terminal and sends the command above"){.didact})

If everything is ok, after the build phase finishes, you should see the Camel integration running. When a new Salesforce Case is created, the integration will print "New Case <salesforce-id>" in the terminal window. After creating a ServiceNow Incident Request it will print "Created Incident with number <servicenow-incident-number>". It will then update the Salesforce Case and print "Updated Case with Incident number <servicenow-incident-number>". 

When running in dev mode, you can change the integration code and let Camel K redeploy the changes automatically.

To try this feature,
[open the `SalesforceToServiceNow.java` file](didact://?commandId=vscode.openFolder&projectFilePath=SalesforceToServiceNow.java&completion=Opened%20the%20SalesforceToServiceNow.java%20file "Opens the SalesforceToServiceNow.java file"){.didact} 
and change "New Case" to "Holy new Case Batman", then save the file.
You should see the new integration starting up in the terminal window and replacing the old one.

[**To exit dev mode and terminate the execution**, just click here](didact://?commandId=vscode.didact.sendNamedTerminalCtrlC&text=camelTerm&completion=Camel%20K%20saas%20integration%20interrupted. "Interrupt the current operation on the terminal"){.didact} 
or hit `ctrl+c` on the terminal window.

> **Note:** When you terminate a "dev mode" execution, also the remote integration will be deleted. This gives the experience of a local program execution, but the integration is actually running in the remote cluster.

To keep the integration running and not linked to the terminal, you can run it without "dev mode", just run:

```
kamel run SalesforceToServiceNow.java
```
([^ execute](didact://?commandId=vscode.didact.sendNamedTerminalAString&text=camelTerm$$kamel%20run%20SalesforceToServiceNow.java&completion=Camel%20K%20saas%20integration%20run. "Opens a new terminal and sends the command above"){.didact})

After executing the command, you should be able to see it among running integrations:

```
oc get integrations
```
([^ execute](didact://?commandId=vscode.didact.sendNamedTerminalAString&text=camelTerm$$oc%20get%20integrations&completion=Getting%20running%20integrations. "Opens a new terminal and sends the command above"){.didact})

An integration named `salesforce-to-service-now` should be present in the list and it should be in status `Running`. There's also a `kamel get` command which is an alternative way to list all running integrations.

> **Note:** the first time you've run the integration, an IntegrationKit (basically, a container image) has been created for it and 
> it took some time for this phase to finish. When you run the integration a second time, the existing IntegrationKit is reused 
> (if possible) and the integration reaches the "Running" state much faster.
>

Even if it's not running in dev mode, you can still see the logs of the integration using the following command:

```
kamel log salesforce-to-service-now
```
([^ execute](didact://?commandId=vscode.didact.sendNamedTerminalAString&text=camelTerm$$kamel%20log%20salesforce-to-service-now&completion=Show%20integration%20logs. "Opens a new terminal and sends the command above"){.didact})

The last parameter ("salesforce-to-service-now") is the name of the running integration for which you want to display the logs.

[**Click here to terminate the log stream**](didact://?commandId=vscode.didact.sendNamedTerminalCtrlC&text=camelTerm&completion=Camel%20K%20salesforce-to-service-now%20integration%20interrupted. "Interrupt the current operation on the terminal"){.didact} 
or hit `ctrl+c` on the terminal window.

> **Note:** Your IDE may provide an "Apache Camel K Integrations" panel where you can see the list of running integrations and also open a window to display the logs.

## 4. Uninstall

To cleanup everything, execute the following command:

```oc delete project camel-saas```

([^ execute](didact://?commandId=vscode.didact.sendNamedTerminalAString&text=camelTerm$$oc%20delete%20project%20camel-saas&completion=Removed%20the%20project%20from%20the%20cluster. "Cleans up the cluster after running the example"){.didact})
